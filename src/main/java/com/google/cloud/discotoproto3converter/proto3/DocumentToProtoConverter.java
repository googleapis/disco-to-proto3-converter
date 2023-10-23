/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.cloud.discotoproto3converter.proto3;

import com.google.cloud.discotoproto3converter.disco.Document;
import com.google.cloud.discotoproto3converter.disco.Inflector;
import com.google.cloud.discotoproto3converter.disco.Method;
import com.google.cloud.discotoproto3converter.disco.Name;
import com.google.cloud.discotoproto3converter.disco.Schema;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DocumentToProtoConverter {

  private static final Pattern RELATIVE_LINK = Pattern.compile("(?<linkName>\\[[\\w\\s]+])\\(/");

  private final ProtoFile protoFile = new ProtoFile();
  private final Set<String> serviceIgnoreSet;
  private final Set<String> messageIgnoreSet;
  private final String relativeLinkPrefix;
  private final boolean enumsAsStrings;

  public DocumentToProtoConverter(
      Document document,
      String documentFileName,
      Set<String> serviceIgnoreSet,
      Set<String> messageIgnoreSet,
      String relativeLinkPrefix,
      boolean enumsAsStrings) {
    this.serviceIgnoreSet = serviceIgnoreSet;
    this.messageIgnoreSet = messageIgnoreSet;
    this.relativeLinkPrefix = relativeLinkPrefix;
    this.protoFile.setMetadata(readDocumentMetadata(document, documentFileName));
    this.enumsAsStrings = enumsAsStrings;
    readSchema(document);
    readResources(document);
    cleanupEnumNamingConflicts();
    this.protoFile.setHasLroDefinitions(applyLroConfiguration());
    this.protoFile.setHasAnyFields(checkAnyFields());
    convertEnumFieldsToStrings();
  }

  public ProtoFile getProtoFile() {
    return protoFile;
  }

  private ProtoFileMetadata readDocumentMetadata(Document document, String documentFileName) {
    return new ProtoFileMetadata(
        documentFileName,
        document.name(),
        document.version(),
        document.revision(),
        // TODO: Calculate package name from scopes
        "google.cloud." + document.name() + "." + document.version(),
        document.version());
  }

  private void readSchema(Document document) {
    for (Map.Entry<String, Schema> entry : document.schemas().entrySet()) {
      schemaToField(entry.getValue(), true, "*** readSchema\n");
    }
    for (Message message : protoFile.getMessages().values()) {
      resolveReferences(message);
    }
  }

  private void resolveReferences(Message message) {
    for (Field field : message.getFields()) {
      Message valueType = field.getValueType();
      if (valueType.isRef()) { // replace the field object with a link to the message it references by name
        field.setValueType(protoFile.getMessages().get(valueType.getName()));
      } else { // recurse in
        resolveReferences(valueType);
      }
    }
  }

  private boolean checkForAllowedAnyFields(Message message) {
    return checkForAllowedAnyFields(message, message.getName());
  }

  private boolean checkForAllowedAnyFields(Message message, String previousFieldPath) {
    // We want to we recursively check every child and don't short-circuit when haveAny becomes
    // true, as we rely on the side effect (exception) to signal a google.protobuf.Any in an
    // unsuported location.
    boolean haveAny = false;
    for (Field field : message.getFields()) {
      Message valueType = field.getValueType();
      String currentFieldPath = previousFieldPath + "." + field.getName();
      // System.err.printf("**-- currentFieldPath: %s\n", currentFieldPath);
      if (valueType == Message.PRIMITIVES.get("any")) {
        if (currentFieldPath.endsWith("error.details")) {
          System.err.printf("Found ANY field at %s\n", currentFieldPath);
          haveAny = true;
        } else {
          throw new IllegalArgumentException("illegal ANY type not under \"*.error.details\": " + currentFieldPath);
        }
      } else {
        haveAny = checkForAllowedAnyFields(field.getValueType(), currentFieldPath) || haveAny;
      }
    }
    return haveAny;
  }

  // If there is a naming conflict between two or more enums in the same message, convert all
  // enum types to strings (happens rarely, but happens).
  private void cleanupEnumNamingConflicts() {
    Message stringType = Message.PRIMITIVES.get("string");
    for (Message message : protoFile.getMessages().values()) {
      int enumFieldsCount = 0;
      Set<String> enumFieldsNames = new HashSet<>();
      Set<String> enumNames = new HashSet<>();
      Set<String> duplicatedEnumFields = new HashSet<>();

      for (Message nestedEnum : message.getEnums()) {
        for (Field enumField : nestedEnum.getFields()) {
          if (!enumFieldsNames.add(enumField.getName())) {
            duplicatedEnumFields.add(enumField.getName());
          }
        }
      }
      for (Message nestedEnum : message.getEnums()) {
        enumNames.add(nestedEnum.getName());
        // Set<String> removed = new HashSet<>();
        StringBuilder extraValues = new StringBuilder();
        nestedEnum
            .getFields()
            .removeIf(
                a -> {
                  if (duplicatedEnumFields.contains(a.getName())) {
                    extraValues.append('\n').append(a.getName());
                    return true;
                  }
                  return false;
                });

        if (extraValues.length() > 0) {
          nestedEnum.appendDescription(
              "\nAdditional supported values which may be not listed in the enum directly due"
                  + " to technical reasons:"
                  + extraValues);
        }
      }

      if (enumFieldsCount > enumFieldsNames.size()) {
        message.getEnums().clear();
        SortedSet<Field> newFields = new TreeSet<>();
        for (Field f : message.getFields()) {
          if (enumNames.contains(f.getValueType().getName())) {
            String desc = sanitizeDescr(f.getDescription());
            newFields.add(
                new Field(
                    f.getName(),
                    stringType,
                    f.isRepeated(),
                    f.isOptional(),
                    f.getKeyType(),
                    desc,
                    f.isFirstInOrder()));
          } else {
            newFields.add(f);
          }
        }

        message.getFields().clear();
        message.getFields().addAll(newFields);
      }
    }
  }

  private void convertEnumFieldsToStrings() {
    if (!this.enumsAsStrings) {
      return;
    }

    Message stringType = Message.PRIMITIVES.get("string");
    for (Message message : protoFile.getMessages().values()) {
      SortedSet<Field> enumFields = new TreeSet<>();
      for (Field field : message.getFields()) {
        // Enums declared in Operation must remain intact
        if (field
            .getOptions()
            .stream()
            .anyMatch(a -> "google.cloud.operation_field".equals(a.getName()))) {
          enumFields.clear();
          break;
        }
        if (field.getValueType().isEnum()) {
          enumFields.add(field);
        }
      }

      message.getFields().removeAll(enumFields);
      for (Field f : enumFields) {
        String desc = f.getDescription()
            + "\nCheck the "
            + f.getValueType().getName()
            + " enum for the list of possible values.";
        message
            .getFields()
            .add(
                new Field(
                    f.getName(),
                    stringType,
                    f.isRepeated(),
                    f.isOptional(),
                    f.getKeyType(),
                    desc,
                    f.isFirstInOrder()));
      }
    }
  }

  private boolean checkAnyFields() {
    boolean haveAny = false;
    // Note that we only check for Any fields for messages rooted in requests and responses. We
    // don't want to initiate the request in submessages that will be included in those.
    for (GrpcService service : protoFile.getServices().values()) {
      for (GrpcMethod method : service.getMethods()) {
        // It's important these checks are not short-circuited!
        boolean inInput = checkForAllowedAnyFields(method.getInput());  // should we disallow error.details.Any on inputs?
        boolean inOutput = checkForAllowedAnyFields(method.getOutput());
        haveAny = haveAny || inInput || inOutput;
      }
    }
    return haveAny;
  }

  private boolean applyLroConfiguration() {
    //
    // 1. Set `operation_field` annotations (Operation fields essential for LRO).
    //
    Message operation = protoFile.getMessages().get("Operation");
    if (operation == null) {
      // This service does not have LRO - do nothing;
      return false;
    }

    Map<ProtoOptionValues, Field> opFields = new HashMap<>();
    for (Field field : operation.getFieldsWithNumbers().values()) {
      String fieldName = field.getName();
      if ("name".equals(fieldName)
          || ("id".equals(fieldName) && !opFields.containsKey(ProtoOptionValues.NAME))) {
        opFields.put(ProtoOptionValues.NAME, field);
      } else if ("done".equals(fieldName)
          || ("status".equals(fieldName) && !opFields.containsKey(ProtoOptionValues.STATUS))) {
        opFields.put(ProtoOptionValues.STATUS, field);
      } else if (fieldName.contains("error")) {
        if (fieldName.contains("code")) {
          opFields.put(ProtoOptionValues.ERROR_CODE, field);
        } else if (fieldName.contains("message")) {
          opFields.put(ProtoOptionValues.ERROR_MESSAGE, field);
        }

        opFields.putIfAbsent(ProtoOptionValues.ERROR_CODE, field);
        opFields.putIfAbsent(ProtoOptionValues.ERROR_MESSAGE, field);
      }
    }

    if (opFields.size() != 4) {
      // Could not detect all LRO fields in the Operation message object.
      return false;
    }

    for (Map.Entry<ProtoOptionValues, Field> entry : opFields.entrySet()) {
      entry
          .getValue()
          .getOptions()
          .add(createOption("google.cloud.operation_field", entry.getKey()));
    }

    //
    // 2. Set `operation_response_field` and `operation_polling_method` annotations. Find the
    // LRO polling methods within the API (the ones, which are used to poll for Operation status).
    //
    Map<String, Map<String, Field>> pollingServiceMessageFieldsMap = new HashMap<>();

    String noMatchPollingServiceName = null;
    for (GrpcService service : protoFile.getServices().values()) {
      for (GrpcMethod method : service.getMethods()) {
        if (!operation.equals(method.getOutput())) {
          continue;
        }

        Optional<Option> optHttp = method
            .getOptions()
            .stream()
            .filter(a -> "google.api.http".equals(a.getName()))
            .findFirst();

        if (!optHttp.isPresent()
            || !optHttp.get().getProperties().containsKey("get")
            || (optHttp.get().getProperties().containsKey("post")
                && "Wait".equals(method.getName()))) {
          continue;
        }

        if (pollingServiceMessageFieldsMap.containsKey(service.getName())) {
          throw new IllegalArgumentException(
              service.getName() + " service has more than one LRO polling method");
        }

        method.getOptions().add(createOption("google.cloud.operation_polling_method", true));

        Map<String, Field> pollingServiceMessageFields = new HashMap<>();
        for (Field pollingMessageField : method.getInput().getFieldsWithNumbers().values()) {
          String fieldName = pollingMessageField.getName();
          if ("name".equals(fieldName) || "operation".equals(fieldName) || "id".equals(fieldName)) {
            // this field will be populated from the response (Operation) message, thus adding
            // `operation_response_field` option to it.
            pollingMessageField
                .getOptions()
                .add(
                    createOption(
                        "google.cloud.operation_response_field",
                        opFields.get(ProtoOptionValues.NAME).getName()));
          } else {
            // These fields will be populated from initial request message, thus putting them in
            // pollingServiceMessageFields map, which will be used to populate
            // `operation_request_field` annotations.
            pollingServiceMessageFields.put(fieldName, pollingMessageField);
          }
        }

        // A temprorary workaround to detect polling service to use if there is no match.
        if (pollingServiceMessageFields.size() == 1
            && pollingServiceMessageFields.containsKey("parent_id")) {
          noMatchPollingServiceName = service.getName();
        }
        pollingServiceMessageFieldsMap.put(service.getName(), pollingServiceMessageFields);
      }
    }

    if (pollingServiceMessageFieldsMap.isEmpty()) {
      // No polling services found.
      return true;
    }

    //
    // 3. Set `operation_request_field` and `operation_service` annotation. Find the LRO methods
    // (the ones which start Operation within the API).
    //
    for (GrpcService service : protoFile.getServices().values()) {
      for (GrpcMethod method : service.getMethods()) {
        if (!operation.equals(method.getOutput())) {
          continue;
        }

        Optional<Option> optHttp = method
            .getOptions()
            .stream()
            .filter(a -> "google.api.http".equals(a.getName()))
            .findFirst();

        if (!optHttp.isPresent()
            || optHttp.get().getProperties().containsKey("get")
            || (optHttp.get().getProperties().containsKey("post")
                && "Wait".equals(method.getName()))) {
          continue;
        }

        // Find the LRO polling message with the most intersecting fields with the LRO initiating
        // message. The "winner" will determine which polling service should be used for this
        // LRO initiating method.
        List<Field[]> matchingFieldPairsCandidate = null;
        String pollingServiceCandidate = null;
        int matchingFieldPairsMax = 0;

        for (Map.Entry<String, Map<String, Field>> entry : pollingServiceMessageFieldsMap.entrySet()) {
          List<Field[]> matchingFieldPairs = new ArrayList<>();
          Map<String, Field> pollingMessageFields = entry.getValue();
          for (Field initiatingMessageField : method.getInput().getFieldsWithNumbers().values()) {
            Field pollingMessageField = pollingMessageFields.get(initiatingMessageField.getName());
            if (pollingMessageField != null && pollingMessageField.equals(initiatingMessageField)) {
              matchingFieldPairs.add(new Field[] { initiatingMessageField, pollingMessageField });
            }
          }

          matchingFieldPairsMax = Math.max(matchingFieldPairsMax, matchingFieldPairs.size());
          if (matchingFieldPairs.size() < pollingMessageFields.size()) {
            continue;
          }
          if (matchingFieldPairsCandidate == null
              || matchingFieldPairs.size() > matchingFieldPairsCandidate.size()) {
            matchingFieldPairsCandidate = matchingFieldPairs;
            pollingServiceCandidate = entry.getKey();
          }
        }

        if (pollingServiceCandidate == null) {
          if (matchingFieldPairsMax == 0) {
            pollingServiceCandidate = noMatchPollingServiceName;
            matchingFieldPairsCandidate = Collections.emptyList();
          } else {
            // No matching polling service was found for the potential LRO method, keep the method
            // as a regular unary method (do not add LRO annotatioins to it).
            throw new IllegalArgumentException(
                method.getName() + " has no matching polling service");
          }
        }

        method
            .getOptions()
            .add(createOption("google.cloud.operation_service", pollingServiceCandidate));
        for (Field[] fieldPair : matchingFieldPairsCandidate) {
          fieldPair[0]
              .getOptions()
              .add(createOption("google.cloud.operation_request_field", fieldPair[1].getName()));
        }
      }
    }

    return true;
  }

  private Option createOption(String optionName, Object scalarValue) {
    Option option = new Option(optionName);
    option.getProperties().put("", scalarValue);
    return option;
  }

  private Field schemaToField(Schema sch, boolean optional, String debugPreviousPath) {
    String name = Name.anyCamel(sch.key()).toCapitalizedLowerUnderscore();
    String description = sch.description();
    Message valueType = null;
    boolean repeated = false;
    Message keyType = null;
    String debugCurentPath = debugPreviousPath + String.format("SCHEMA: %s\n%s\n----\n", name, description);

    switch (sch.type()) {
      case ANY:
        System.err.printf("*** vchudnov: ERROR trace:\n%s", debugCurentPath);
        valueType = Message.PRIMITIVES.get("any");
        break;
      // throw new IllegalArgumentException("Any type detected in schema: " + sch);
      case ARRAY:
        repeated = true;
        break;
      case BOOLEAN:
        valueType = Message.PRIMITIVES.get("bool");
        break;
      case EMPTY:
        valueType = new Message(sch.reference(), true, false, null);
      case INTEGER:
        switch (sch.format()) {
          case INT32:
            valueType = Message.PRIMITIVES.get("int32");
            break;
          case INT64:
            valueType = Message.PRIMITIVES.get("int64");
            break;
          case UINT32:
            valueType = Message.PRIMITIVES.get("uint32");
            break;
          case UINT64:
            valueType = Message.PRIMITIVES.get("uint64");
            break;
          case FIXED32:
            valueType = Message.PRIMITIVES.get("fixed32");
            break;
          case FIXED64:
            valueType = Message.PRIMITIVES.get("fixed64");
            break;
        }
        break;
      case NUMBER:
        switch (sch.format()) {
          case FLOAT:
            valueType = Message.PRIMITIVES.get("float");
            break;
          case DOUBLE:
            valueType = Message.PRIMITIVES.get("double");
            break;
        }
        break;
      case OBJECT:
        if (sch.additionalProperties() != null) {
          repeated = true;
          keyType = Message.PRIMITIVES.get("string");
        } else {
          valueType = new Message(getMessageName(sch), false, false, sanitizeDescr(description));
        }
        break;
      case STRING:
        if (sch.isEnum() && !"".equals(sch.getIdentifier())) {
          valueType = constructEnumMessage(
              getMessageName(sch, true), description, sch.enumValues(), sch.enumDescriptions());
        } else {
          switch (sch.format()) {
            case INT64:
              valueType = Message.PRIMITIVES.get("int64");
              break;
            case UINT64:
              valueType = Message.PRIMITIVES.get("uint64");
              break;
            case FIXED64:
              valueType = Message.PRIMITIVES.get("fixed64");
              break;
            default:
              valueType = Message.PRIMITIVES.get("string");
              break;
          }
        }
        break;
    }

    if (repeated) {
      Field subField = schemaToField(keyType == null ? sch.items() : sch.additionalProperties(), true, debugCurentPath);
      valueType = subField.getValueType();
    }

    Field field = new Field(name, valueType, repeated, optional, keyType, sanitizeDescr(description), false);
    if (sch.type() == Schema.Type.EMPTY) {
    } else if (Message.PRIMITIVES.containsKey(valueType.getName())) {
      return field;
    }

    // this is where we recurse for OBJECT
    for (Map.Entry<String, Schema> entry : sch.properties().entrySet()) {
      Field valueTypeField = schemaToField(entry.getValue(), true, debugCurentPath);
      valueType.getFields().add(valueTypeField);
      if (valueTypeField.getValueType().isEnum()) {
        valueType.getEnums().add(valueTypeField.getValueType());
      }
    }

    if (valueType.isEnum()) {
      // Enums are always nested
      return field;
    }

    Message existingMessage = protoFile.getMessages().get(valueType.getName());

    if (existingMessage == null || existingMessage.isRef()) {
      putAllMessages(valueType.getName(), valueType);
    } else if (!valueType.isRef()) {
      if (valueType.getDescription() != null
          && existingMessage.getDescription() != null
          // not clear on the reason for this
          && valueType.getDescription().length() < existingMessage.getDescription().length()) {
        putAllMessages(valueType.getName(), valueType);
      }
      if (!valueType.equals(existingMessage)) {
        if (!"Errors".equals(valueType.getName())) {
          // ManagedInstanceLastAttempt has the following ridiculous internal Errors message
          // definition:
          // message ManagedInstanceLastAttempt {
          //   message Errors {
          //       message Errors {}
          //       repeated Errors errors = 1;
          //   }
          //   Errors errors = 1;
          // }
          // (i.e. Errors inside Errors with repeated Errors inside and singular Errors outside
          // (O_o))
          // This is the only place where something like this happens, so simply ignoring it for
          // now.
          throw new IllegalArgumentException(
              "Message collision detected. Existing message = "
                  + existingMessage
                  + ", new message = "
                  + valueType);
        }
      }
    }
    return field;
  }

  private Message constructEnumMessage(
      String name, String description, List<String> enumVals, List<String> enumDescs) {
    Message enumMessage = new Message(name, false, true, sanitizeDescr(description));

    String dummyDesc = "A value indicating that the enum field is not set.";
    String dummyFieldName = Name.anyCamel("Undefined", name).toUpperUnderscore();
    Message emptyType = Message.PRIMITIVES.get("");
    Field dummyField = new Field(dummyFieldName, emptyType, false, false, null, sanitizeDescr(dummyDesc), true);
    enumMessage.getFields().add(dummyField);

    Iterator<String> valIter = enumVals.iterator();
    Iterator<String> descIter = enumDescs.iterator();
    while (valIter.hasNext() && descIter.hasNext()) {
      String desc = sanitizeDescr(descIter.next());
      Field enumField = new Field(valIter.next(), emptyType, false, false, null, desc, false);
      if (dummyField.getName().equals(enumField.getName())) {
        continue;
      }
      enumMessage.getFields().add(enumField);
    }

    return enumMessage;
  }

  private String getMessageName(Schema sch) {
    String messageName = sch.getIdentifier();
    if (Character.isLowerCase(messageName.charAt(0))) {
      messageName = Name.anyCamel(messageName).toUpperCamel();
    }
    return messageName;
  }

  private String getMessageName(Schema sch, Boolean isEnum) {
    String messageName = sch.getIdentifier();
    // For the enum name start with uppercase letter, add the "Enum" suffix.
    if (isEnum && Character.isUpperCase(messageName.charAt(0))) {
      return Name.anyCamel(messageName).toUpperCamel() + "Enum";
    }
    return getMessageName(sch);
  }

  private void readResources(Document document) {
    String endpointSuffix = document.baseUrl().substring(document.rootUrl().length());
    endpointSuffix = endpointSuffix.startsWith("/") ? endpointSuffix : '/' + endpointSuffix;
    endpointSuffix = endpointSuffix.replaceAll("/$", "");
    String endpoint = document.rootUrl().replaceAll("(^https://)|(/$)", "");

    for (Map.Entry<String, List<Method>> entry : document.resources().entrySet()) {
      String grpcServiceName = Name.anyCamel(entry.getKey()).toUpperCamel();
      GrpcService service = new GrpcService(grpcServiceName, getServiceDescription(grpcServiceName));
      if (serviceIgnoreSet.contains(service.getName())) {
        // Ignore the service (as early as possible to avoid dependency failures on previously
        // ignored request messages used in this service).
        continue;
      }
      service.getOptions().add(createOption("google.api.default_host", endpoint));

      Set<String> authScopes = new HashSet<>();

      for (Method method : entry.getValue()) {
        if (authScopes.isEmpty()) {
          authScopes.addAll(method.scopes());
        } else {
          authScopes.retainAll(method.scopes());
        }

        // Request
        String requestName = getRpcMessageName(method, "request").toUpperCamel();
        String methodname = getRpcMethodName(method).toUpperCamel();
        String inputDescription = getInputMessageDescription(grpcServiceName, methodname);
        Message input = new Message(requestName, false, false, sanitizeDescr(inputDescription));
        String httpOptionPath = method.flatPath();
        // The map key is the parameter identifier in Discovery doc, while the map value is the
        // parameter name in proto file (they may use different naming styles). The body parameter
        // has empty string as a key because in Discovery doc it does not have a designated name.
        Map<String, String> methodSignatureParamNames = new LinkedHashMap<>();
        for (String requiredParamName : method.requiredParamNames()) {
          methodSignatureParamNames.put(requiredParamName, null);
        }

        for (Schema pathParam : method.pathParams().values()) {
          boolean required = methodSignatureParamNames.containsKey(pathParam.getIdentifier());
          Field pathField = schemaToField(pathParam, !required, "readResources:A:  ");
          if (required) {
            Option opt = createOption("google.api.field_behavior", ProtoOptionValues.REQUIRED);
            pathField.getOptions().add(opt);
            methodSignatureParamNames.put(pathParam.getIdentifier(), pathField.getName());
          }
          input.getFields().add(pathField);
          httpOptionPath = httpOptionPath.replace(
              "{" + pathParam.getIdentifier() + "}", "{" + pathField.getName() + "}");
        }

        for (Schema queryParam : method.queryParams().values()) {
          boolean required = methodSignatureParamNames.containsKey(queryParam.getIdentifier());
          Field queryField = schemaToField(queryParam, !required, "readResources:B:  ");
          if (required) {
            Option opt = createOption("google.api.field_behavior", ProtoOptionValues.REQUIRED);
            queryField.getOptions().add(opt);
            methodSignatureParamNames.put(queryParam.getIdentifier(), queryField.getName());
          }
          input.getFields().add(queryField);
          if (queryField.getValueType().isEnum()) {
            input.getEnums().add(queryField.getValueType());
          }
        }

        // TODO: add logic to determine non-required method_signature options
        Option methodHttpOption = new Option("google.api.http");
        methodHttpOption
            .getProperties()
            .put(method.httpMethod().toLowerCase(), endpointSuffix + "/" + httpOptionPath);

        if (method.request() != null) {
          Message request = protoFile.getMessages().get(method.request().reference());
          String requestFieldName = Name.anyCamel(request.getName(), "resource").toLowerUnderscore();
          String description = getMessageBodyDescription();
          Field bodyField = new Field(
              requestFieldName, request, false, false, null, sanitizeDescr(description), false);
          bodyField
              .getOptions()
              .add(createOption("google.api.field_behavior", ProtoOptionValues.REQUIRED));
          input.getFields().add(bodyField);
          methodHttpOption.getProperties().put("body", requestFieldName);
          methodSignatureParamNames.put("", requestFieldName);
        }

        putAllMessages(requestName, input);

        // Response
        Message output;
        if (method.response() != null) {
          output = protoFile.getMessages().get(method.response().reference());
        } else {
          String responseName = getRpcMessageName(method, "response").toUpperCamel();
          String outputDescription = getOutputMessageDescription(grpcServiceName, methodname);
          output = new Message(responseName, false, false, outputDescription);
          putAllMessages(responseName, output);
        }

        // Method
        GrpcMethod grpcMethod = new GrpcMethod(methodname, input, output, sanitizeDescr(method.description()));
        grpcMethod.getOptions().add(methodHttpOption);
        Option requiredMethodSignatureOption = createOption(
            "google.api.method_signature",
            String.join(",", methodSignatureParamNames.values()));
        grpcMethod.getOptions().add(requiredMethodSignatureOption);
        // TODO: design heuristic for other useful method signatures with optional fields

        service.getMethods().add(grpcMethod);
      }

      service
          .getOptions()
          .add(createOption("google.api.oauth_scopes", String.join(",", authScopes)));
      protoFile.getServices().put(service.getName(), service);
    }
  }

  private void putAllMessages(String messageName, Message message) {
    if (!messageIgnoreSet.contains(messageName)) {
      protoFile.getMessages().put(messageName, message);
    }
  }

  private String getInputMessageDescription(String serviceName, String methodName) {
    return "A request message for "
        + serviceName
        + "."
        + methodName
        + ". See the method description for details.";
  }

  private String getOutputMessageDescription(String serviceName, String methodName) {
    return "A response message for "
        + serviceName
        + "."
        + methodName
        + ". See the method description for details.";
  }

  private String getMessageBodyDescription() {
    return "The body resource for this request";
  }

  private String getServiceDescription(String serviceName) {
    return "The " + serviceName + " API.";
  }

  private Name getRpcMessageName(Method method, String suffix) {
    String[] pieces = method.id().split("\\.");
    String methodName = pieces[pieces.length - 1];
    String resourceName = pieces[pieces.length - 2];
    if (!method.isPluralMethod()) {
      resourceName = Inflector.singularize(resourceName);
    }
    return Name.anyCamel(methodName, resourceName, suffix);
  }

  private Name getRpcMethodName(Method method) {
    String[] pieces = method.id().split("\\.");
    String methodName = pieces[pieces.length - 1];
    return Name.anyCamel(methodName);
  }

  private String sanitizeDescr(String description) {
    if (description == null
        || description.isEmpty()
        || relativeLinkPrefix == null
        || relativeLinkPrefix.isEmpty()) {
      return description;
    }

    // It is an inefficient way of doing it, but it does not really matter for all possible
    // practical
    // applications of this converter app.
    Matcher m = RELATIVE_LINK.matcher(description);
    String sanitizedDescription = description;
    while (m.find()) {
      sanitizedDescription = m.replaceFirst(m.group("linkName") + "(" + relativeLinkPrefix + "/");
      m = RELATIVE_LINK.matcher(sanitizedDescription);
    }

    return sanitizedDescription.replace(
        "{$api_version}", protoFile.getMetadata().getProtoPkgVersion());
  }
}

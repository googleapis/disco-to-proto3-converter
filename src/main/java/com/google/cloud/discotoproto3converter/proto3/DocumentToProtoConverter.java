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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DocumentToProtoConverter {
  private final ProtoFile protoFile;
  private final Map<String, Message> allMessages = new LinkedHashMap<>();
  private final Map<String, GrpcService> allServices = new LinkedHashMap<>();
  private final Map<String, Option> allResourceOptions = new LinkedHashMap<>();
  private Set<String> serviceIgnoreSet;
  private Set<String> messageIgnoreSet;

  public DocumentToProtoConverter(
      Document document,
      String documentFileName,
      Set<String> serviceIgnoreSet,
      Set<String> messageIgnoreSet) {
    this.serviceIgnoreSet = serviceIgnoreSet;
    this.messageIgnoreSet = messageIgnoreSet;
    this.protoFile = readDocumentMetadata(document, documentFileName);
    readSchema(document);
    readResources(document);
    cleanupEnumNamingConflicts();
  }

  public ProtoFile getProtoFile() {
    return protoFile;
  }

  public Map<String, Message> getAllMessages() {
    return Collections.unmodifiableMap(allMessages);
  }

  public Map<String, GrpcService> getAllServices() {
    return Collections.unmodifiableMap(allServices);
  }

  public Map<String, Option> getAllResourceOptions() {
    return Collections.unmodifiableMap(allResourceOptions);
  }

  private ProtoFile readDocumentMetadata(Document document, String documentFileName) {
    return new ProtoFile(
        documentFileName,
        document.name(),
        document.version(),
        document.revision(),
        // TODO: Calculate package name from scopes
        "google.cloud." + document.name() + "." + document.version());
  }

  private void readSchema(Document document) {
    for (Map.Entry<String, Schema> entry : document.schemas().entrySet()) {
      schemaToField(entry.getValue());
    }
    for (Message message : allMessages.values()) {
      resolveReferences(message);
    }
  }

  private void resolveReferences(Message message) {
    for (Field field : message.getFields()) {
      Message valueType = field.getValueType();
      if (valueType.isRef()) {
        field.setValueType(allMessages.get(valueType.getName()));
      } else {
        resolveReferences(valueType);
      }
    }
  }

  // If there is a naming conflict between two or more enums in the same message, convert all
  // enum types to strings (happens rarely, but happens).
  private void cleanupEnumNamingConflicts() {
    Message stringType = Message.PRIMITIVES.get("string");
    for (Message message : allMessages.values()) {
      int enumFieldsCount = 0;
      Set<String> enumFieldsNames = new HashSet<>();
      Set<String> enumNames = new HashSet<>();
      for (Message nestedMessage : message.getEnums()) {
        enumNames.add(nestedMessage.getName());
        List<String> enumFields =
            nestedMessage.getFields().stream().map(Field::getName).collect(Collectors.toList());
        enumFieldsCount += enumFields.size();
        enumFieldsNames.addAll(enumFields);
      }
      if (enumFieldsCount > enumFieldsNames.size()) {
        message.getEnums().clear();
        ListIterator<Field> fields = message.getFields().listIterator();
        while (fields.hasNext()) {
          Field f = fields.next();
          if (enumNames.contains(f.getValueType().getName())) {
            fields.set(
                new Field(
                    f.getName(), stringType, f.isRepeated(), f.getKeyType(), f.getDescription()));
          }
        }
      }
    }
  }

  private Field schemaToField(Schema sch) {
    String name = Name.anyCamel(sch.key()).toLowerUnderscore();
    String description = sch.description();
    Message valueType = null;
    boolean repeated = false;
    Message keyType = null;

    switch (sch.type()) {
      case ANY:
        throw new IllegalArgumentException("Any type detected in schema: " + sch);
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
          valueType = new Message(getMessageName(sch), false, false, description);
        }
        break;
      case STRING:
        if (sch.isEnum() && !"".equals(sch.getIdentifier())) {
          valueType =
              constructEnumMessage(
                  getMessageName(sch), description, sch.enumValues(), sch.enumDescriptions());
        } else {
          valueType = Message.PRIMITIVES.get("string");
        }
        break;
    }

    if (repeated) {
      Field subField = schemaToField(keyType == null ? sch.items() : sch.additionalProperties());
      valueType = subField.getValueType();
    }

    Field field = new Field(name, valueType, repeated, keyType, description);
    if (sch.type() == Schema.Type.EMPTY) {
    } else if (Message.PRIMITIVES.containsKey(valueType.getName())) {
      return field;
    }

    for (Map.Entry<String, Schema> entry : sch.properties().entrySet()) {
      Field valueTypeField = schemaToField(entry.getValue());
      valueType.getFields().add(valueTypeField);
      if (valueTypeField.getValueType().isEnum()) {
        valueType.getEnums().add(valueTypeField.getValueType());
      }
    }

    if (valueType.isEnum()) {
      // Enums are always nested
      return field;
    }

    Message existingMessage = allMessages.get(valueType.getName());

    if (existingMessage == null || existingMessage.isRef()) {
      putAllMessages(valueType.getName(), valueType);
    } else if (!valueType.isRef()) {
      if (valueType.getDescription() != null
          && existingMessage.getDescription() != null
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
    Message enumMessage = new Message(name, false, true, description);

    String dummyDesc = "A value indicating that the enum field is not set.";
    String dummyFieldName = Name.anyCamel("Undefined", name).toUpperUnderscore();

    Field dummyField =
        new Field(dummyFieldName, Message.PRIMITIVES.get(""), false, null, dummyDesc);
    enumMessage.getFields().add(dummyField);

    Iterator<String> valIter = enumVals.iterator();
    Iterator<String> descIter = enumDescs.iterator();
    while (valIter.hasNext() && descIter.hasNext()) {
      Field enumField =
          new Field(valIter.next(), Message.PRIMITIVES.get(""), false, null, descIter.next());
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

  private void readResources(Document document) {
    String endpointSuffix = document.baseUrl().substring(document.rootUrl().length());
    endpointSuffix = endpointSuffix.startsWith("/") ? endpointSuffix : '/' + endpointSuffix;
    endpointSuffix = endpointSuffix.replaceAll("/$", "");
    String endpoint = document.rootUrl().replaceAll("(^https://)|(/$)", "");

    for (Map.Entry<String, List<Method>> entry : document.resources().entrySet()) {
      String grpcServiceName = Name.anyCamel(entry.getKey()).toUpperCamel();
      GrpcService service =
          new GrpcService(grpcServiceName, getServiceDescription(grpcServiceName));
      if (serviceIgnoreSet.contains(service.getName())) {
        // Ignore the service (as early as possible to avoid dependency failures on previously
        // ignored request messages used in this service).
        continue;
      }
      Option defaultHostOpt = new Option("google.api.default_host");
      defaultHostOpt.getProperties().put("", endpoint);
      service.getOptions().add(defaultHostOpt);

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
        Message input = new Message(requestName, false, false, inputDescription);
        String httpOptionPath = method.flatPath();
        // The map key is the parameter identifier in Discovery doc, while the map value is the
        // parameter name in proto file (they may use different naming styles). The body parameter
        // has empty string as a key because in Discovery doc it does not have a designated name.
        Map<String, String> methodSignatureParamNames = new LinkedHashMap<>();
        for (String requiredParamName : method.requiredParamNames()) {
          methodSignatureParamNames.put(requiredParamName, null);
        }

        for (Schema pathParam : method.pathParams().values()) {
          Field pathField = schemaToField(pathParam);
          if (methodSignatureParamNames.containsKey(pathParam.getIdentifier())) {
            pathField.getOptions().add(getFieldBehaviorOption("REQUIRED"));
            methodSignatureParamNames.put(pathParam.getIdentifier(), pathField.getName());
          }
          input.getFields().add(pathField);
          httpOptionPath =
              httpOptionPath.replace(
                  "{" + pathParam.getIdentifier() + "}", "{" + pathField.getName() + "}");
        }

        for (Schema queryParam : method.queryParams().values()) {
          Field queryField = schemaToField(queryParam);
          if (methodSignatureParamNames.containsKey(queryParam.getIdentifier())) {
            queryField.getOptions().add(getFieldBehaviorOption("REQUIRED"));
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
          Message request = allMessages.get(method.request().reference());
          String requestFieldName =
              Name.anyCamel(request.getName(), "resource").toLowerUnderscore();
          String description = getMessageBodyDescription();
          Field bodyField = new Field(requestFieldName, request, false, null, description);
          bodyField.getOptions().add(getFieldBehaviorOption("REQUIRED"));
          input.getFields().add(bodyField);
          methodHttpOption.getProperties().put("body", requestFieldName);
          methodSignatureParamNames.put("", requestFieldName);
        }

        Option requiredMethodSignatureOption = new Option("google.api.method_signature");
        requiredMethodSignatureOption
            .getProperties()
            .put("", String.join(",", methodSignatureParamNames.values()));

        putAllMessages(requestName, input);

        // Response
        Message output;
        if (method.response() != null) {
          output = allMessages.get(method.response().reference());
        } else {
          String responseName = getRpcMessageName(method, "response").toUpperCamel();
          String outputDescription = getOutputMessageDescription(grpcServiceName, methodname);
          output = new Message(responseName, false, false, outputDescription);
          putAllMessages(responseName, output);
        }

        // Method
        GrpcMethod grpcMethod = new GrpcMethod(methodname, input, output, method.description());
        grpcMethod.getOptions().add(methodHttpOption);
        grpcMethod.getOptions().add(requiredMethodSignatureOption);
        // TODO: design heuristic for other useful method signatures with optional fields

        service.getMethods().add(grpcMethod);
      }

      Option authScopesOpt = new Option("google.api.oauth_scopes");
      authScopesOpt.getProperties().put("", String.join(",", authScopes));
      service.getOptions().add(authScopesOpt);
      allServices.put(service.getName(), service);
    }
  }

  private Option getFieldBehaviorOption(String optionValue) {
    Option fieldBehaviorOption = new Option("google.api.field_behavior");
    fieldBehaviorOption.getProperties().put("", optionValue);
    return fieldBehaviorOption;
  }

  private void putAllMessages(String messageName, Message message) {
    if (!messageIgnoreSet.contains(messageName)) {
      allMessages.put(messageName, message);
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
}

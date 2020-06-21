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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DocumentToProtoConverter {
  private final String packageName;
  private final Map<String, Message> allMessages = new LinkedHashMap<>();
  private final Map<String, GrpcService> allServices = new LinkedHashMap<>();
  private final Map<String, Option> allResourceOptions = new LinkedHashMap<>();

  public DocumentToProtoConverter(Document document) {
    this.packageName = "google.cloud." + document.name() + "." + document.version();
    readSchema(document);
    readResources(document);
    cleanupEnumNamingConflicts();
  }

  public String getPackageName() {
    return packageName;
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
    Message stringType = Field.PRIMITIVES.get("string");
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
            fields.set(new Field(f.getName(), stringType, f.isRepeated(), f.getKeyType()));
          }
        }
      }
    }
  }

  private Field schemaToField(Schema sch) {
    String name = Name.anyCamel(sch.key()).toLowerUnderscore();
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
        valueType = Field.PRIMITIVES.get("bool");
        break;
      case EMPTY:
        valueType = new Message(sch.reference(), true, false);
      case INTEGER:
        switch (sch.format()) {
          case INT32:
            valueType = Field.PRIMITIVES.get("int32");
            break;
          case INT64:
            valueType = Field.PRIMITIVES.get("int64");
            break;
          case UINT32:
            valueType = Field.PRIMITIVES.get("uint32");
            break;
          case UINT64:
            valueType = Field.PRIMITIVES.get("uint64");
            break;
        }
        break;
      case NUMBER:
        switch (sch.format()) {
          case FLOAT:
            valueType = Field.PRIMITIVES.get("float");
            break;
          case DOUBLE:
            valueType = Field.PRIMITIVES.get("double");
            break;
        }
        break;
      case OBJECT:
        if (sch.additionalProperties() != null) {
          repeated = true;
          keyType = Field.PRIMITIVES.get("string");
        } else {
          valueType = new Message(getMessageName(sch), false, false);
        }
        break;
      case STRING:
        if (sch.isEnum()) {
          valueType = new Message(getMessageName(sch), false, true);
          for (String enumValue : sch.enumValues()) {
            valueType.getFields().add(new Field(enumValue, Field.PRIMITIVES.get(""), false, null));
          }
        } else {
          valueType = Field.PRIMITIVES.get("string");
        }
        break;
    }

    if (repeated) {
      Field subField = schemaToField(keyType == null ? sch.items() : sch.additionalProperties());
      valueType = subField.getValueType();
    }

    Field field = new Field(name, valueType, repeated, keyType);
    if (sch.type() == Schema.Type.EMPTY) {
    } else if (Field.PRIMITIVES.containsKey(valueType.getName())) {
      return field;
    }

    for (Map.Entry<String, Schema> entry : sch.properties().entrySet()) {
      // This breaks due to wrong camel-snake naming styles conversion bug.
      if ("vlanTag8021q".equals(entry.getValue().key())) {
        continue;
      }
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
      allMessages.put(valueType.getName(), valueType);
    } else if (!valueType.isRef() && !valueType.equals(existingMessage)) {
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
        // (i.e. Errors inside Errors with repeated Errors inside and singular Errors outside (O_o))
        // This is the only place where something like this happens, so simply ignoring it for now.
        throw new IllegalArgumentException(
            "Message collision detected. Existing message = "
                + existingMessage
                + ", new message = "
                + valueType);
      }
    }
    return field;
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
      GrpcService service = new GrpcService(grpcServiceName);
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
        Message input = new Message(requestName, false, false);
        String httpOptionPath = method.flatPath();
        for (Schema pathParam : method.pathParams().values()) {
          Field pathField = schemaToField(pathParam);
          input.getFields().add(pathField);
          httpOptionPath =
              httpOptionPath.replace(
                  "{" + pathParam.getIdentifier() + "}", "{" + pathField.getName() + "}");
        }

        for (Schema queryParam : method.queryParams().values()) {
          Field queryField = schemaToField(queryParam);
          input.getFields().add(queryField);
          if (queryField.getValueType().isEnum()) {
            input.getEnums().add(queryField.getValueType());
          }
        }
        Option methodHttpOption = new Option("google.api.http");
        methodHttpOption
            .getProperties()
            .put(method.httpMethod().toLowerCase(), endpointSuffix + "/" + httpOptionPath);

        if (method.request() != null) {
          Message request = allMessages.get(method.request().reference());
          String requestFieldName =
              Name.anyCamel(request.getName(), "resource").toLowerUnderscore();
          input.getFields().add(new Field(requestFieldName, request, false, null));
          methodHttpOption.getProperties().put("body", requestFieldName);
        }
        allMessages.put(requestName, input);

        // Response
        Message output;
        if (method.response() != null) {
          output = allMessages.get(method.response().reference());
        } else {
          String responseName = getRpcMessageName(method, "response").toUpperCamel();
          output = new Message(responseName, false, false);
          allMessages.put(responseName, output);
        }

        String methodname = getRpcMethodName(method).toUpperCamel();

        // Method
        GrpcMethod grpcMethod = new GrpcMethod(methodname, input, output);
        grpcMethod.getOptions().add(methodHttpOption);

        service.getMethods().add(grpcMethod);
      }

      Option authScopesOpt = new Option("google.api.oauth_scopes");
      authScopesOpt.getProperties().put("", String.join(",", authScopes));
      service.getOptions().add(authScopesOpt);
      allServices.put(service.getName(), service);
    }
  }

  private static Name getRpcMessageName(Method method, String suffix) {
    String[] pieces = method.id().split("\\.");
    String methodName = pieces[pieces.length - 1];
    String resourceName = pieces[pieces.length - 2];
    if (!method.isPluralMethod()) {
      resourceName = Inflector.singularize(resourceName);
    }
    return Name.anyCamel(methodName, resourceName, suffix);
  }

  private static Name getRpcMethodName(Method method) {
    String[] pieces = method.id().split("\\.");
    String methodName = pieces[pieces.length - 1];
    return Name.anyCamel(methodName);
  }
}

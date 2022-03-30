/*
 * Copyright 2022 Google LLC
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// This class does not intend to do a fully-functional merge of two proto models, instead it focuses
// on merging currently known potential discrepancies between old and new proto files:
// - Missing field in a new proto file
// - Missing values in enums
// - Mismatching service method google.api.method_signature option values
//
// Additional merging functionality is expected to be added to this class as needed.
public class ProtoMerger {
  // This method will modify content of the newProto parameter
  public void merge(ProtoFile newProto, ProtoFile oldProto) {
    mergeMessages(newProto.getMessages(), oldProto.getMessages());
    mergeServices(newProto.getServices(), oldProto.getServices());
  }

  private void mergeServices(
      Map<String, GrpcService> newServices, Map<String, GrpcService> oldServices) {
    for (GrpcService oldService : oldServices.values()) {
      GrpcService newService = newServices.get(oldService.getName());

      Map<String, GrpcMethod> newMethodsMap = new HashMap<>();
      for (GrpcMethod newMethod : newService.getMethods()) {
        newMethodsMap.put(newMethod.getName(), newMethod);
      }

      for (GrpcMethod oldMethod : oldService.getMethods()) {
        mergeMethodSignatureOption(newMethodsMap.get(oldMethod.getName()), oldMethod);
      }
    }
  }

  private Optional<Option> getOption(List<Option> options, String optionName) {
    return options.stream().filter(a -> optionName.equals(a.getName())).findFirst();
  }

  private void mergeMethodSignatureOption(GrpcMethod newMethod, GrpcMethod oldMethod) {
    Optional<Option> oldMethodSignatureOption =
        getOption(oldMethod.getOptions(), "google.api.method_signature");

    if (oldMethodSignatureOption.isPresent()) {
      Optional<Option> newMethodSignatureOption =
          getOption(newMethod.getOptions(), "google.api.method_signature");
      if (newMethodSignatureOption.isPresent()) {
        newMethodSignatureOption
            .get()
            .getProperties()
            .putAll(oldMethodSignatureOption.get().getProperties());
      }
    }
  }

  private void mergeMessages(Map<String, Message> newMessages, Map<String, Message> oldMessages) {
    for (Map.Entry<String, Message> entry : oldMessages.entrySet()) {
      Message oldMessage = entry.getValue();
      Message newMessage = newMessages.get(entry.getKey());

      // Merge fields
      mergeFields(newMessage, oldMessage, newMessages);

      // Merge enums
      Map<String, Message> newEnumsMap = new HashMap<>();
      for (Message nestedEnum : newMessage.getEnums()) {
        newEnumsMap.put(nestedEnum.getName(), nestedEnum);
      }
      for (Message oldEnum : oldMessage.getEnums()) {
        Message newEnum = newEnumsMap.get(oldEnum.getName());
        mergeFields(newEnum, oldEnum, newMessages);
      }
    }
  }

  private void mergeFields(
      Message newMessage, Message oldMessage, Map<String, Message> newMessages) {
    // Merge fields
    for (Field oldField : oldMessage.getFields()) {
      // compareTo() will be used by contains() to search for the field, not equals().
      if (!newMessage.getFields().contains(oldField)) {
        newMessage.getFields().add(copyField(oldField, newMessages));
      }
    }
  }

  // We need to replace references for message types from oldMessages to newMessages.
  // Despite message types having the same names, they are two independently created
  // sets of objects.
  private Field copyField(Field oldField, Map<String, Message> newMessages) {
    Message valueType = Message.PRIMITIVES.get(oldField.getValueType().getName());
    if (valueType == null) {
      valueType = newMessages.get(oldField.getValueType().getName());
    }
    Message keyType = null;
    if (oldField.getKeyType() != null) {
      keyType = Message.PRIMITIVES.get(oldField.getValueType().getName());
      if (keyType == null) {
        keyType = newMessages.get(oldField.getKeyType().getName());
      }
    }
    Field mergedField =
        new Field(
            oldField.getName(),
            valueType,
            oldField.isRepeated(),
            oldField.isOptional(),
            keyType,
            oldField.getDescription(),
            oldField.isFirstInOrder());

    // Do not do deep copy for options because they are all generic immutable objects (strings or
    // enums)
    mergedField.getOptions().addAll(oldField.getOptions());
    return mergedField;
  }
}

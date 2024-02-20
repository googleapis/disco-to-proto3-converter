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
import java.util.stream.Collectors;

// This class does not intend to do a fully-functional merge of two proto models, instead it focuses
// on merging currently known potential discrepancies between old and new proto files:
// - missing field in a new proto file;
// - missing values in enums;
// - mismatching service method google.api.method_signature option values (old overwrite new);
// - mismatching field annotations (old overwrite new).
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
      mergeEnums(newMessages, oldMessage, newMessage);
    }
  }

  private void mergeEnums(
      Map<String, Message> newMessages, Message oldMessage, Message newMessage) {
    Map<String, Message> newEnumsMap = new HashMap<>();
    for (Message nestedEnum : newMessage.getEnums()) {
      newEnumsMap.put(nestedEnum.getName(), nestedEnum);
    }
    for (Message oldEnum : oldMessage.getEnums()) {
      Message newEnum = newEnumsMap.get(oldEnum.getName());
      mergeFields(newEnum, oldEnum, newMessages);
    }
  }

  private void mergeFields(
      Message newMessage, Message oldMessage, Map<String, Message> newMessages) {
    // Merge fields
    Map<String, Field> newFieldsMap =
        newMessage.getFields().stream().collect(Collectors.toMap(ProtoElement::getName, f -> f));

    for (Field oldField : oldMessage.getFields()) {
      // compareTo() will be used by contains() to search for the field, not equals().
      if (!newMessage.getFields().contains(oldField)) {
        // Copy removed field
        Field copiedField = copyField(null, oldField, newMessages);
        if (copiedField != null) {
          newMessage.getFields().add(copiedField);
        }
      } else {
        Field newField = newFieldsMap.get(oldField.getName());
        // Copy missing options if a new field has fewer options than old field.
        // This is a very primitive merge logic. Add a proper merge logic if ever needed.
        if (oldField.getOptions().size() > newField.getOptions().size()
            || oldField.isOptional() != newField.isOptional()) {
          Field copiedField = copyField(newField, oldField, newMessages);
          if (copiedField != null) {
            newMessage.getFields().remove(newField);
            newMessage.getFields().add(copiedField);
          }
        }
      }
    }
  }

  // We need to replace references for message types from oldMessages to newMessages.
  // Despite message types having the same names, they are two independently created
  // sets of objects.
  private Field copyField(Field newField, Field oldField, Map<String, Message> newMessages) {
    Message valueType = null;
    if (oldField.getValueType() != null) {
      valueType = Message.PRIMITIVES.get(oldField.getValueType().getName());
      if (valueType == null) {
        valueType = newMessages.get(oldField.getValueType().getName());
      }
    }
    Message keyType = null;
    if (oldField.getKeyType() != null) {
      keyType = Message.PRIMITIVES.get(oldField.getValueType().getName());
      if (keyType == null) {
        keyType = newMessages.get(oldField.getKeyType().getName());
      }
    }

    if (valueType == null && keyType == null) {
      // TODO(https://github.com/googleapis/disco-to-proto3-converter/issues/113): Investigate how
      // this happens. It seems to be related to messages with `Any` fields.
      return null;
    }

    Field mergedField =
        new Field(
            oldField.getName(),
            valueType,
            oldField.isRepeated(),
            oldField.isOptional(),
            keyType,
            newField != null ? newField.getDescription() : oldField.getDescription(),
            oldField.isFirstInOrder());

    // Do not do deep copy for options because they are all generic immutable objects (strings or
    // enums)
    mergedField.getOptions().addAll(oldField.getOptions());
    return mergedField;
  }
}

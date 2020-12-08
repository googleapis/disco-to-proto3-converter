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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class Message extends ProtoElement {
  public static final Map<String, Message> PRIMITIVES = new HashMap<>();

  static {
    PRIMITIVES.put("bool", new Message("bool", false, false, null));
    PRIMITIVES.put("string", new Message("string", false, false, null));
    PRIMITIVES.put("int32", new Message("int32", false, false, null));
    PRIMITIVES.put("fixed32", new Message("fixed32", false, false, null));
    PRIMITIVES.put("uint32", new Message("uint32", false, false, null));
    PRIMITIVES.put("int64", new Message("int64", false, false, null));
    PRIMITIVES.put("fixed64", new Message("fixed64", false, false, null));
    PRIMITIVES.put("unit64", new Message("unit64", false, false, null));
    PRIMITIVES.put("float", new Message("float", false, false, null));
    PRIMITIVES.put("double", new Message("double", false, false, null));
    PRIMITIVES.put("", new Message("", false, true, null));
  }

  private final String name;
  private final List<Field> fields = new ArrayList<>();
  private final boolean ref;
  // Not used for now, and hopefully will never be used.
  private final List<Message> enums = new ArrayList<>();
  private boolean isEnum;

  public Message(String name, boolean ref, boolean isEnum, String description) {
    super(description);
    this.name = name;
    this.ref = ref;
    this.isEnum = isEnum;
  }

  public List<Field> getFields() {
    return fields;
  }

  public Map<Integer, Field> getFieldsWithNumbers() {
    Map<Integer, Field> fieldsWithNumbers = new LinkedHashMap<>();

    List<Field> sortedFields =
        fields
            .stream()
            .skip(isEnum ? 1 : 0)
            .sorted(Comparator.comparing(Field::getName))
            .collect(Collectors.toList());
    if (isEnum) {
      // Make sure that the first element of an enum is always the dummy value
      sortedFields.add(0, fields.get(0));
    }

    for (Field f : sortedFields) {
      if (fieldsWithNumbers.isEmpty() && isEnum()) {
        // For enum, the first element should always have number 0.
        fieldsWithNumbers.put(0, f);
        continue;
      }
      int fieldNumber = getFieldNumber(f.getName());
      while (fieldsWithNumbers.containsKey(fieldNumber)) {
        fieldNumber = incrementFieldNumber(fieldNumber);
      }
      fieldsWithNumbers.put(fieldNumber, f);
    }

    return fieldsWithNumbers;
  }

  // All the "magic numbers" come from the proto3 spec:
  // https://developers.google.com/protocol-buffers/docs/proto3#assigning_field_numbers
  private int getFieldNumber(String fieldName) {
    // TODO: Use 2^29 instead of 2^28 once the following is fixed:
    // https://github.com/protocolbuffers/protobuf/issues/8114
    int fieldNumber = (fieldName.hashCode() << 4) >>> 4;
    if (fieldNumber == 0 || (fieldNumber >= 19000 && fieldNumber <= 19999)) {
      fieldNumber = 20000 + ((fieldNumber % 19000) + 1) * 268147;
    }
    return fieldNumber;
  }

  private int incrementFieldNumber(int fieldNumber) {
    int incrementedFieldNumber = fieldNumber + 1;
    if ((fieldNumber >= 19000 && fieldNumber <= 19999) || fieldNumber >= (1 << 28)) {
      incrementedFieldNumber = 20000;
    }
    return incrementedFieldNumber;
  }

  public List<Message> getEnums() {
    return enums;
  }

  public String getName() {
    return name;
  }

  public boolean isRef() {
    return ref;
  }

  public boolean isEnum() {
    return isEnum;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    Message message = (Message) o;
    return ref == message.ref
        && isEnum == message.isEnum
        && Objects.equals(name, message.name)
        && Objects.equals(fields, message.fields)
        && Objects.equals(enums, message.enums);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, fields, ref, enums, isEnum);
  }

  @Override
  public String toString() {
    return name;
  }
}

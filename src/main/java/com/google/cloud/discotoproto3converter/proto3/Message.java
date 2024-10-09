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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

public class Message extends ProtoElement<Message> {
  public static final Map<String, Message> PRIMITIVES = new HashMap<>();

  static {
    PRIMITIVES.put("bool", new Message("bool", false, false, null));
    PRIMITIVES.put("string", new Message("string", false, false, null));
    PRIMITIVES.put("int32", new Message("int32", false, false, null));
    PRIMITIVES.put("fixed32", new Message("fixed32", false, false, null));
    PRIMITIVES.put("uint32", new Message("uint32", false, false, null));
    PRIMITIVES.put("int64", new Message("int64", false, false, null));
    PRIMITIVES.put("fixed64", new Message("fixed64", false, false, null));
    PRIMITIVES.put("uint64", new Message("uint64", false, false, null));
    PRIMITIVES.put("float", new Message("float", false, false, null));
    PRIMITIVES.put("double", new Message("double", false, false, null));
    PRIMITIVES.put("", new Message("", false, true, null));

    // This isn't technically a primitive, but it is a fundamental well-known-type with no a priori
    // structure.
    //
    // TODO: If we start accepting additional well-known types, create a specific data structure for
    // those rather than overloading "PRIMITIVES".
    PRIMITIVES.put("google.protobuf.Any", new Message("google.protobuf.Any", false, false, null));
    PRIMITIVES.put(
        "google.protobuf.Value", new Message("google.protobuf.Value", false, false, null));
    PRIMITIVES.put(
        "google.protobuf.ListValue", new Message("google.protobuf.ListValue", false, false, null));
    PRIMITIVES.put(
        "google.protobuf.Struct", new Message("google.protobuf.Struct", false, false, null));
  }

  private final SortedSet<Field> fields = new TreeSet<>();
  private final boolean ref;
  private final SortedSet<Message> enums = new TreeSet<>();
  private final boolean isEnum;

  public Message(String name, boolean ref, boolean isEnum, String description) {
    super(name, description);
    this.ref = ref;
    this.isEnum = isEnum;
  }

  public SortedSet<Field> getFields() {
    return fields;
  }

  public Map<Integer, Field> getFieldsWithNumbers() {
    Map<Integer, Field> fieldsWithNumbers = new LinkedHashMap<>();

    for (Field f : fields) {
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
    int fieldNumber = (fieldName.hashCode() << 3) >>> 3;
    if (fieldNumber == 0 || (fieldNumber >= 19000 && fieldNumber <= 19999)) {
      fieldNumber = 20000 + ((fieldNumber % 19000) + 1) * 536314;
    }
    return fieldNumber;
  }

  private int incrementFieldNumber(int fieldNumber) {
    int incrementedFieldNumber = fieldNumber + 1;
    if ((fieldNumber >= 19000 && fieldNumber <= 19999) || fieldNumber >= (1 << 29)) {
      incrementedFieldNumber = 20000;
    }
    return incrementedFieldNumber;
  }

  public SortedSet<Message> getEnums() {
    return enums;
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
        && Objects.equals(fields, message.fields)
        && Objects.equals(enums, message.enums);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), fields, ref, enums, isEnum);
  }

  @Override
  public String toString() {
    return getName();
  }
}

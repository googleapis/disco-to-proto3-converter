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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Field extends ProtoElement {
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
  private Message valueType;
  private final boolean repeated;
  private Message keyType;
  private final List<Option> options = new ArrayList<>();

  public Field(
      String name, Message valueType, boolean repeated, Message keyType, String description) {
    super(description);
    this.name = name;
    this.valueType = valueType;
    this.repeated = repeated;
    this.keyType = keyType;
  }

  public String getName() {
    return name;
  }

  public void setValueType(Message valueType) {
    this.valueType = valueType;
  }

  public Message getValueType() {
    return valueType;
  }

  public boolean isRepeated() {
    return repeated;
  }

  public void setKeyType(Message keyType) {
    this.keyType = keyType;
  }

  public Message getKeyType() {
    return keyType;
  }

  public List<Option> getOptions() {
    return options;
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
    Field field = (Field) o;
    return repeated == field.repeated
        && Objects.equals(name, field.name)
        && Objects.equals(valueType, field.valueType)
        && Objects.equals(keyType, field.keyType)
        && Objects.equals(options, field.options);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, valueType, repeated, keyType, options);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    if (repeated) {
      if (keyType != null) {
        sb.append("map<").append(keyType).append(", ").append(valueType).append(">");
      } else {
        sb.append("repeated ").append(valueType);
      }
    } else {
      sb.append(valueType);
    }
    if (sb.length() > 0) {
      sb.append(' ');
    }

    return sb.append(name).toString();
  }

  public String toString(int fieldIndex) {
    return toString() + " = " + fieldIndex;
  }
}

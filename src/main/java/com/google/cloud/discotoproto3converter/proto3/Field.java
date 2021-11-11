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
import java.util.List;
import java.util.Objects;

// compareTo() == 0 and equals() are inconsistent for this implementation
public class Field extends ProtoElement<Field> {
  private Message valueType;
  private final boolean repeated;
  private final boolean optional;
  private Message keyType;
  private final List<Option> options = new ArrayList<>();
  private final boolean firstInOrder;

  public Field(
      String name,
      Message valueType,
      boolean repeated,
      boolean optional,
      Message keyType,
      String description,
      boolean firstInOrder) {
    super(name, description);
    this.valueType = valueType;
    this.repeated = repeated;
    this.optional = optional;
    this.keyType = keyType;
    // This exists to cover the `UNDEFINED = 0` enum field, which should always go first
    this.firstInOrder = firstInOrder;
  }

  @Override
  public int compareTo(Field o) {
    int rv = -Boolean.compare(this.firstInOrder, o.firstInOrder);
    return rv == 0 ? super.compareTo(o) : rv;
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

  public boolean isOptional() {
    return optional;
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

  public boolean isFirstInOrder() {
    return firstInOrder;
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
        && Objects.equals(valueType, field.valueType)
        && Objects.equals(keyType, field.keyType)
        && Objects.equals(options, field.options);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), valueType, repeated, keyType, options);
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
      if (optional) {
        sb.append("optional ");
      }
      sb.append(valueType);
    }
    if (sb.length() > 0) {
      sb.append(' ');
    }

    return sb.append(getName()).toString();
  }

  public String toString(int fieldIndex) {
    return toString() + " = " + fieldIndex;
  }
}

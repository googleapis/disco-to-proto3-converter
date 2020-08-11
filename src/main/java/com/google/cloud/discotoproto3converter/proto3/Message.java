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

public class Message extends ProtoElement {
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

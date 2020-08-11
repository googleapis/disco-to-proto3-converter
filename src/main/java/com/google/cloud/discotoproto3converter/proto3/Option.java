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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class Option extends ProtoElement {
  private final String name;
  private final Map<String, String> properties = new LinkedHashMap<>();

  public Option(String name) {
    super(null);
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public Map<String, String> getProperties() {
    return properties;
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
    Option option = (Option) o;
    return Objects.equals(name, option.name) && Objects.equals(properties, option.properties);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name, properties);
  }

  @Override
  public String toString() {
    return name;
  }
}

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

import java.util.Objects;

// compareTo() == 0 and equals are inconsistent for this implementation
// The ProtoElement objects are expected to be stored in SrotedMap/SortedSet containers
public class ProtoElement<T extends ProtoElement<T>> implements Comparable<T> {
  private final String description;
  private final String name;

  public ProtoElement(String name, String description) {
    this.name = name;
    this.description = description;
  }

  @Override
  public int compareTo(T o) {
    return this.getName().compareTo(o.getName());
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProtoElement<?> that = (ProtoElement<?>) o;
    return Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }
}

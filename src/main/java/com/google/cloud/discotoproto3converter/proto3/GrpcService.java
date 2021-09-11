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
import java.util.SortedSet;
import java.util.TreeSet;

public class GrpcService extends ProtoElement<GrpcService> {
  private final SortedSet<GrpcMethod> methods = new TreeSet<>();
  private final List<Option> options = new ArrayList<>();

  public GrpcService(String name, String description) {
    super(name, description);
  }

  public SortedSet<GrpcMethod> getMethods() {
    return methods;
  }

  public List<Option> getOptions() {
    return options;
  }

  @Override
  public String toString() {
    return getName();
  }
}

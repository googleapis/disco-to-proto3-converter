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

// compareTo() == 0 and equals are inconsistent for this implementation
public class GrpcMethod extends ProtoElement<GrpcMethod> {
  private final Message input;
  private final Message output;
  private final List<Option> options = new ArrayList<>();

  public GrpcMethod(String name, Message input, Message output, String description) {
    super(name, description);
    this.input = input;
    this.output = output;
  }

  public List<Option> getOptions() {
    return options;
  }

  public Message getInput() {
    return input;
  }

  public Message getOutput() {
    return output;
  }

  @Override
  public String toString() {
    return "rpc " + getName() + "(" + input + ") returns (" + output + ")";
  }
}

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

package com.google.cloud.discotoproto3converter;

import com.google.cloud.discotoproto3converter.proto3.Proto3Writer;
import java.io.IOException;

public class DiscoToProto3ConverterApp extends ConverterApp {
  public DiscoToProto3ConverterApp() {
    super(new Proto3Writer());
  }

  public static void main(String[] args) throws IOException {
    DiscoToProto3ConverterApp converterApp = new DiscoToProto3ConverterApp();
    System.err.println("*** vchudnov: In main!\n");
    converterApp.convert(args);
  }
}

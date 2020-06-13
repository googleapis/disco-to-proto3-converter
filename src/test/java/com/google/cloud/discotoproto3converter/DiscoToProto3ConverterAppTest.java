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

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

public class DiscoToProto3ConverterAppTest {

  @Test
  public void convert() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path outputDir = Files.createTempDirectory("disco-to-proto3-converter");

    Path discoveryDocPath = Paths.get("src", "test", "resources", "compute.v1.small.json");
    app.convert(discoveryDocPath.toString(), outputDir.toString(), "compute.proto");

    Path prefix = Paths.get("google", "cloud", "compute", "v1");

    Path generatedFilePath = Paths.get(outputDir.toString(), prefix.toString(), "compute.proto");
    String actualBody = readFile(generatedFilePath);

    Path baselineFilePath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.proto.baseline");
    String baselineBody = readFile(baselineFilePath);

    assertEquals(baselineBody, actualBody);
  }

  public static String readFile(Path path) throws IOException {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }
}

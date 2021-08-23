/*
 * Copyright 2021 Google LLC
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
import org.junit.Before;
import org.junit.Test;

public class ServiceConfigGeneratorAppTest {
  private Path outputDir;

  @Before
  public void setUp() throws IOException {
    outputDir = Files.createTempDirectory("disco-to-proto3-converter");
    outputDir.toFile().deleteOnExit();
  }

  @Test
  public void convert() throws IOException {
    ServiceConfigGeneratorApp app = new ServiceConfigGeneratorApp();
    Path discoveryDocPath = Paths.get("src", "test", "resources", "compute.v1.small.json");
    Path prefix = Paths.get("google", "cloud", "compute", "v1");
    Path generatedFilePath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute_grpc_service_config.json");

    app.convert(
        discoveryDocPath.toString(),
        generatedFilePath.toString(),
        "",
        "",
        "https://cloud.google.com");

    String actualBody = readFile(generatedFilePath);

    Path baselineFilePath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute_grpc_service_config.json.baseline");
    String baselineBody = readFile(baselineFilePath);

    assertEquals(baselineBody, actualBody);
  }

  public static String readFile(Path path) throws IOException {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }
}

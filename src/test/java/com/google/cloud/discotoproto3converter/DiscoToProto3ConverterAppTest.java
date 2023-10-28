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
import org.junit.Before;
import org.junit.Test;


public class DiscoToProto3ConverterAppTest {
  private Path outputDir;

  @Before
  public void setUp() throws IOException {
    outputDir = Files.createTempDirectory("disco-to-proto3-converter");
    // outputDir.toFile().deleteOnExit();
  }

  @Test
  public void convert() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.json");
    Path generatedFilePath = Paths.get(outputDir.toString(), prefix.toString(), "compute.proto");

    app.convert(
        discoveryDocPath.toString(),
        null,
        generatedFilePath.toString(),
        "",
        "",
        "https://cloud.google.com",
        "false",
        "true");

    String actualBody = readFile(generatedFilePath);

    Path baselineFilePath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.proto.baseline");
    String baselineBody = readFile(baselineFilePath);

    assertEquals(baselineBody, actualBody);
  }

  @Test
  public void convertWithIgnorelist() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.json");
    Path generatedFilePath = Paths.get(outputDir.toString(), prefix.toString(), "compute.proto");
    app.convert(
        discoveryDocPath.toString(),
        null,
        generatedFilePath.toString(),
        "Addresses,RegionOperations",
        "Operation,AddressList,AddressesScopedList,Warning,Warnings,Data,Error,"
            + "Errors,AddressAggregatedList,AggregatedListAddressesRequest,"
            + "InsertAddressRequest,ListAddressesRequest,InsertAddressRequest,"
            + "GetRegionOperationRequest",
        "",
        "false",
        "true");

    String actualBody = readFile(generatedFilePath);

    Path baselineFilePath =
        Paths.get(
            "src", "test", "resources", prefix.toString(), "compute.ignorelist.proto.baseline");
    String baselineBody = readFile(baselineFilePath);
    assertEquals(baselineBody, actualBody);
  }

  @Test
  public void convertEnumsAsStrings() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.json");
    Path generatedFilePath = Paths.get(outputDir.toString(), prefix.toString(), "compute.proto");

    app.convert(
        discoveryDocPath.toString(),
        null,
        generatedFilePath.toString(),
        "",
        "",
        "https://cloud.google.com",
        "true",
        "true");

    String actualBody = readFile(generatedFilePath);

    Path baselineFilePath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.strings.proto.baseline");
    String baselineBody = readFile(baselineFilePath);

    assertEquals(baselineBody, actualBody);
  }

    @Test
  public void convertAnyFieldInError() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.error-any.json");
    Path generatedFilePath = Paths.get(outputDir.toString(), prefix.toString(), "compute.error-any.proto");
    System.out.printf("*** output path: %s\n", generatedFilePath.toString());

    app.convert(
        discoveryDocPath.toString(),
        null,
        generatedFilePath.toString(),
        "",
        "",
        "https://cloud.google.com",
        "true",
        "true");

    String actualBody = readFile(generatedFilePath);

    Path baselineFilePath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.error-any.proto.baseline");
    String baselineBody = readFile(baselineFilePath);

    assertEquals(baselineBody, actualBody);
  }

  @Test
  public void protoParserRoundtripSmallWithComments() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.json");
    Path baselinePath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.proto.baseline");

    Path convPath = Paths.get(outputDir.toString(), prefix.toString(), "compute_converted.proto");
    app.convert(discoPath.toString(), null, convPath.toString(), "", "", null, "false", "false");

    Path parsedPath = Paths.get(outputDir.toString(), prefix.toString(), "compute_parsed.proto");
    app.convert(
        null, baselinePath.toString(), parsedPath.toString(), "", "", null, "false", "false");

    String convertedBody = readFile(convPath);
    String parsedBody = readFile(parsedPath);
    assertEquals(convertedBody, parsedBody);
  }

  @Test
  public void protoParserRoundtripBigNoComments() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1");
    Path discoPath = Paths.get("src", "test", "resources", prefix.toString(), "compute.v1.json");

    Path convPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute_big_converted.proto");
    app.convert(discoPath.toString(), null, convPath.toString(), "", "", null, "false", "false");

    Path parsedPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute_big_parsed.proto");
    app.convert(null, convPath.toString(), parsedPath.toString(), "", "", null, "false", "false");

    String convertedBody = readFile(convPath);
    String parsedBody = readFile(parsedPath);
    assertEquals(convertedBody, parsedBody);
  }

  @Test
  public void convertWithMerge() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.json");
    Path previousProtoPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.proto");

    Path generatedFilePath = Paths.get(outputDir.toString(), prefix.toString(), "compute.proto");

    app.convert(
        discoveryDocPath.toString(),
        previousProtoPath.toString(),
        generatedFilePath.toString(),
        "",
        "",
        "https://cloud.google.com",
        "false",
        "true");

    String actualBody = readFile(generatedFilePath);

    Path baselineFilePath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.merge.proto.baseline");
    String baselineBody = readFile(baselineFilePath);

    assertEquals(baselineBody, actualBody);
  }

  private static String readFile(Path path) throws IOException {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }
}

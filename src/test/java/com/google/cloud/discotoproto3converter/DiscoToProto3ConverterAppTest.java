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
import static org.junit.Assert.assertThrows;

import com.google.cloud.discotoproto3converter.proto3.ConversionConfiguration;
import com.google.cloud.discotoproto3converter.proto3.DocumentToProtoConverter;
import com.google.cloud.discotoproto3converter.proto3.DocumentToProtoConverter.MessageCollisionException;
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
    outputDir.toFile().deleteOnExit();
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
  public void convertWithConfig() throws IOException {
    // In this test, the input config explicitly renames the
    // `schemas.Operation.warnings.warnings.data.data` field to be a proto message `OperationData`,
    // even though the same schema is used elsewhere. The output proto should reflect this (compare
    // with the output proto in the `convert()` test function above), as should the output config.
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.json");
    Path inputConfigPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.config.in.json");

    Path generatedProtoPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute.v1small.config.proto");
    Path generatedOutputConfigPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute.v1small.config.out.json");

    Path baselineProtoPath =
        Paths.get(
            "src", "test", "resources", prefix.toString(), "compute.v1small.config.baseline.proto");
    Path baselineOutputConfigPath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.v1small.config.out.baseline.json");

    System.out.printf(
        "***@Test: convertWithConfig\n"
            + "   Discovery doc:           %s\n"
            + "   Input config:            %s\n"
            + "   Generated proto:         %s\n"
            + "   Baseline proto:          %s\n"
            + "   Generated output config: %s\n"
            + "   Baseline output config:  %s\n",
        discoveryDocPath.toAbsolutePath(),
        inputConfigPath.toAbsolutePath(),
        generatedProtoPath.toAbsolutePath(),
        baselineProtoPath.toAbsolutePath(),
        generatedOutputConfigPath.toAbsolutePath(),
        baselineOutputConfigPath.toAbsolutePath());

    app.convert(
        discoveryDocPath.toString(),
        null,
        generatedProtoPath.toString(),
        inputConfigPath.toString(),
        generatedOutputConfigPath.toString(),
        "",
        "",
        "https://cloud.google.com",
        "false",
        "true");

    String actualBody = readFile(generatedProtoPath);
    String baselineBody = readFile(baselineProtoPath);
    assertEquals(baselineBody.trim(), actualBody.trim());

    String actualConfig = readFile(generatedOutputConfigPath);
    String baselineConfig = readFile(baselineOutputConfigPath);
    assert checkEquivalentJSON(baselineConfig, actualConfig);
  }

  @Test
  public void convertVersioned() throws IOException {
    // Tests that when all the methods for a single service have identical, non-empty "apiVersion"
    // fields in the Discovery file, the proto service gets annotated with the corresponding
    // "api_version" annotation.

    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small-versioned.json");
    Path generatedFilePath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute-versioned.proto");

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
        Paths.get(
            "src", "test", "resources", prefix.toString(), "compute-versioned.proto.baseline");
    String baselineBody = readFile(baselineFilePath);
    System.out.printf(
        "*** @Test: convertVersioned():\n"
            + "    Discovery path: %s\n"
            + "    Generated file: %s\n"
            + "    Baseline file: %s\n",
        discoveryDocPath.toAbsolutePath(),
        generatedFilePath.toAbsolutePath(),
        baselineFilePath.toAbsolutePath());

    assertEquals(baselineBody, actualBody);
  }

  @Test
  public void convertVersionedInconsistent() throws IOException {
    // Tests that when all the methods in a service have inconsistent non-empty "apiVersion" fields
    // in the Discovery file, generation fails.

    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.v1small-versioned-inconsistent.json");
    Path generatedFilePath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute-versioned-inconsistent.proto");

    assertThrows(
        DocumentToProtoConverter.InconsistentAPIVersionsException.class,
        () ->
            app.convert(
                discoveryDocPath.toString(),
                null,
                generatedFilePath.toString(),
                "",
                "",
                "https://cloud.google.com",
                "true",
                "true"));
  }

  @Test
  public void convertVersionedInconsistentEmpty() throws IOException {
    // Tests that when all the methods in a service have inconsistent "apiVersion" fields in the
    // Discovery file, including empty values, generation fails.

    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.v1small-versioned-inconsistent-empty.json");
    Path generatedFilePath =
        Paths.get(
            outputDir.toString(), prefix.toString(), "compute-versioned-inconsistent-empty.proto");

    assertThrows(
        DocumentToProtoConverter.InconsistentAPIVersionsException.class,
        () ->
            app.convert(
                discoveryDocPath.toString(),
                null,
                generatedFilePath.toString(),
                "",
                "",
                "https://cloud.google.com",
                "true",
                "true"));
  }

  @Test
  public void convertVersionedTwoServices() throws IOException {
    // Tests that when methods for two services have consistent "apiVersion" fields in the Discovery
    // file, the proto services get the correct "api_version" annotation, even if the versions of
    // the two services differ.

    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.v1small-versioned-two-services.json");
    Path generatedFilePath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute-versioned-two-services.proto");

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
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute-versioned-two-services.proto.baseline");
    String baselineBody = readFile(baselineFilePath);
    System.out.printf(
        "*** @Test: convertVersionedTwoServices():\n"
            + "    Discovery path: %s\n"
            + "    Generated file: %s\n"
            + "    Baseline file: %s\n",
        discoveryDocPath.toAbsolutePath(),
        generatedFilePath.toAbsolutePath(),
        baselineFilePath.toAbsolutePath());

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
  public void nameCollisionAvoidanceSuccessOneMessageOneService() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.v1small.collision.message-1.service-1.json");
    Path generatedFilePath =
        Paths.get(
            outputDir.toString(),
            prefix.toString(),
            "compute.v1small.collision.message-1.service-1.proto");

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
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.v1small.collision.message-1.service-1.proto.baseline");
    String baselineBody = readFile(baselineFilePath);
    assertEquals(baselineBody, actualBody);
  }

  @Test
  public void nameCollisionAvoidanceFailureTwoMessagesOneService() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.v1small.collision.message-2.service-1.json");
    Path generatedFilePath =
        Paths.get(
            outputDir.toString(),
            prefix.toString(),
            "compute.v1small.collision.message-2.service-1.proto");

    assertThrows(
        java.lang.IllegalArgumentException.class,
        () ->
            app.convert(
                discoveryDocPath.toString(),
                null,
                generatedFilePath.toString(),
                "",
                "",
                "https://cloud.google.com",
                "true",
                "true"));
  }

  @Test
  public void nameCollisionAvoidanceFailureOneMessageTwoServices() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.v1small.collision.message-1.service-2.json");
    Path generatedFilePath =
        Paths.get(
            outputDir.toString(),
            prefix.toString(),
            "compute.v1small.collision.message-1.service-2.proto");

    assertThrows(
        java.lang.IllegalArgumentException.class,
        () ->
            app.convert(
                discoveryDocPath.toString(),
                null,
                generatedFilePath.toString(),
                "",
                "",
                "https://cloud.google.com",
                "true",
                "true"));
  }

  @Test
  public void convertAnyFieldInError() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.error-any.json");
    Path generatedFilePath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute.error-any.proto");
    Path baselineFilePath =
        Paths.get(
            "src", "test", "resources", prefix.toString(), "compute.error-any.proto.baseline");
    System.out.printf(
        "*** @Test: convertAnyFieldInError():\n"
            + "    Discovery path: %s\n"
            + "    Generated file: %s\n"
            + "    Baseline file: %s\n",
        discoveryDocPath.toAbsolutePath(),
        generatedFilePath.toAbsolutePath(),
        baselineFilePath.toAbsolutePath());

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
    String baselineBody = readFile(baselineFilePath);

    assertEquals(baselineBody, actualBody);
  }

  @Test
  public void convertWithIdempotentMerge() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.error-any.json");
    Path generatedFilePath = Paths.get(outputDir.toString(), prefix.toString(), "compute.proto");
    Path baselineFilePath =
        Paths.get(
            "src", "test", "resources", prefix.toString(), "compute.error-any.proto.baseline");

    // This tests that merging a proto with the a proto created from the same inputs yields the same
    // result.
    app.convert(
        discoveryDocPath.toString(),
        baselineFilePath.toString(),
        generatedFilePath.toString(),
        "",
        "",
        "https://cloud.google.com",
        "false",
        "true");

    String actualBody = readFile(generatedFilePath);

    String baselineBody = readFile(baselineFilePath);

    // TODO(https://github.com/googleapis/disco-to-proto3-converter/issues/114): Investigate why
    // this assertion fails, fix, and re-enable. In short, it appears merging protos created from
    // the exact same source is not a no-op, as one would expect.
    //
    // assertEquals(baselineBody, actualBody);
  }

  @Test
  public void convertAnyFieldOutsideError() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get(
            "src", "test", "resources", prefix.toString(), "compute.v1small.nonerror-any.json");
    Path generatedFilePath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute.nonerror-any.proto");

    assertThrows(
        java.lang.IllegalArgumentException.class,
        () ->
            app.convert(
                discoveryDocPath.toString(),
                null,
                generatedFilePath.toString(),
                "",
                "",
                "https://cloud.google.com",
                "true",
                "true"));
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
    Path discoveryPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1.json");
    Path inputConfigPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1.config.input.json");
    Path convertedProtoPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute_big_converted.proto");
    Path parsedProtoPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute_big_parsed.proto");

    app.convert(
        discoveryPath.toString(),
        null,
        convertedProtoPath.toString(),
        inputConfigPath.toString(),
        "",
        "",
        "",
        null,
        "false",
        "false");
    app.convert(
        null,
        convertedProtoPath.toString(),
        parsedProtoPath.toString(),
        inputConfigPath.toString(),
        "",
        "",
        "",
        null,
        "false",
        "false");

    String convertedBody = readFile(convertedProtoPath);
    String parsedBody = readFile(parsedProtoPath);
    assertEquals(convertedBody, parsedBody);
  }

  @Test
  public void protoParserNamingConflict() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1");
    Path discoPath = Paths.get("src", "test", "resources", prefix.toString(), "compute.v1.json");

    Path convPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute_big_converted.proto");
    assertThrows(
        MessageCollisionException.class,
        () ->
            app.convert(
                discoPath.toString(), null, convPath.toString(), "", "", null, "false", "false"));
  }

  @Test
  public void idempotentConversionWithOutputConfig() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1");
    Path discoveryPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1.json");
    Path inputConfigPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1.config.input.json");
    Path firstOutputConfigPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute-v1.config.output-1.json");
    Path secondOutputConfigPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute.v1.config.output-2.json");
    Path firstProtoPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute_big_first.proto");
    Path secondProtoPath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute_big_second.proto");

    System.out.printf(
        "*** @Test: idempotentConversionWithOutputConfig():\n"
            + "    Discovery path: %s\n"
            + "    Input config: %s\n"
            + "    First output config:  %s\n"
            + "    First output proto:   %s\n"
            + "    Second output config: %s\n"
            + "    Second output proto:  %s\n",
        discoveryPath.toAbsolutePath(),
        inputConfigPath.toAbsolutePath(),
        firstOutputConfigPath.toAbsolutePath(),
        firstProtoPath.toAbsolutePath(),
        secondOutputConfigPath.toAbsolutePath(),
        secondProtoPath.toAbsolutePath());

    app.convert(
        discoveryPath.toString(),
        null,
        firstProtoPath.toString(),
        inputConfigPath.toString(),
        firstOutputConfigPath.toString(),
        "",
        "",
        null,
        "false",
        "false",
        "3001-01-01");
    app.convert(
        discoveryPath.toString(),
        null,
        secondProtoPath.toString(),
        firstOutputConfigPath.toString(),
        secondOutputConfigPath.toString(),
        "",
        "",
        null,
        "false",
        "false",
        "3001-01-01");

    assert readFile(firstProtoPath).equals(readFile(secondProtoPath)); // identical output protos
    assert readFile(firstOutputConfigPath)
        .equals(readFile(secondOutputConfigPath)); // identical output configs
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

  @Test
  public void convertAnyFieldWithFormat() throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get("src", "test", "resources", prefix.toString(), "compute.v1small.any-format.json");
    Path generatedFilePath =
        Paths.get(outputDir.toString(), prefix.toString(), "compute.any-format.proto");
    Path baselineFilePath =
        Paths.get(
            "src", "test", "resources", prefix.toString(), "compute.any-format.proto.baseline");
    System.out.printf(
        "*** @Test: convertAnyFieldWithFormat():\n"
            + "    Discovery path: %s\n"
            + "    Generated file: %s\n"
            + "    Baseline file: %s\n",
        discoveryDocPath.toAbsolutePath(),
        generatedFilePath.toAbsolutePath(),
        baselineFilePath.toAbsolutePath());

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
    String baselineBody = readFile(baselineFilePath);
    assertEquals(baselineBody, actualBody);
  }

  @Test
  public void convertRequestMessageNameConflicts() throws IOException {
    // In this test, the Discovery file defines a method
    // Advice.CalendarMode(CalendarModeAdviceRequest). The converter tries to create its own request
    // wrapper message for this method, also called CalendarModeAdviceRequest, which wraps the
    // Discovery-provided request message. The converter notices that the request wrapper message
    // name would be the same as the Discovery request message name, and it changes the name of the
    // wrapper to CalendarModeAdviceRpcRequest.
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.v1small.request-message-name-conflict.json");
    Path generatedFilePath =
        Paths.get(
            outputDir.toString(), prefix.toString(), "compute.request-message-name-conflict.proto");
    Path baselineFilePath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.request-message-name-conflict.proto.baseline");
    System.out.printf(
        "*** @Test: convertRequestMessageNameConflicts():\n"
            + "    Discovery path: %s\n"
            + "    Generated file: %s\n"
            + "    Baseline file: %s\n",
        discoveryDocPath.toAbsolutePath(),
        generatedFilePath.toAbsolutePath(),
        baselineFilePath.toAbsolutePath());

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
    String baselineBody = readFile(baselineFilePath);
    assertEquals(baselineBody, actualBody);
  }

  @Test
  public void convertRequestMessageNameUnrecoverableConflicts() throws IOException {
    // In this test, the Discovery file defines a method
    // Advice.CalendarMode(CalendarModeAdviceRequest). The converter tries to create its own request
    // wrapper message for this method, also called CalendarModeAdviceRequest, which wraps the
    // Discovery-provided request message. The converter notices that the request wrapper message
    // name would be the same as the Discovery request message name, and it tries to change the name
    // of the wrapper to CalendarModeAdviceRpcRequest. However, there is another Discovery message
    // with that new name, so the converter stops trying to rename and throws an exception.
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Path prefix = Paths.get("google", "cloud", "compute", "v1small");
    Path discoveryDocPath =
        Paths.get(
            "src",
            "test",
            "resources",
            prefix.toString(),
            "compute.v1small.request-message-name-conflict-unrecoverable.json");
    Path generatedFilePath =
        Paths.get(
            outputDir.toString(),
            prefix.toString(),
            "compute.request-message-name-conflict-unrecoverable.proto");
    System.out.printf(
        "*** @Test: convertRequestMessageNameUnrecoverableConflicts():\n"
            + "    Discovery path: %s\n"
            + "    Generated file: %s\n",
        discoveryDocPath.toAbsolutePath(), generatedFilePath.toAbsolutePath());

    assertThrows(
        DocumentToProtoConverter.RpcRequestMessageConflictException.class,
        () ->
            app.convert(
                discoveryDocPath.toString(),
                null,
                generatedFilePath.toString(),
                "",
                "",
                "https://cloud.google.com",
                "true",
                "true"));
  }

  private static String readFile(Path path) throws IOException {
    return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
  }

  /**
   * Determines whether two instances have all their important public fields being equal; this
   * excludes the Discovery revision, converter version, and schema summaries.
   */
  public static boolean checkEquivalentJSON(String expected, String actual) {
    return ConversionConfiguration.fromJSON(expected)
        .publicFieldsEqual(ConversionConfiguration.fromJSON(actual), false, false, false);
  }
}

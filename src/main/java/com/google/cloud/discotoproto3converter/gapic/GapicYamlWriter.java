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
package com.google.cloud.discotoproto3converter.gapic;

import com.google.cloud.discotoproto3converter.proto3.ConverterWriter;
import com.google.cloud.discotoproto3converter.proto3.GrpcMethod;
import com.google.cloud.discotoproto3converter.proto3.GrpcService;
import com.google.cloud.discotoproto3converter.proto3.Option;
import com.google.cloud.discotoproto3converter.proto3.ProtoFile;
import com.google.cloud.discotoproto3converter.proto3.ProtoFileMetadata;
import java.io.PrintWriter;
import java.util.Optional;

public class GapicYamlWriter implements ConverterWriter {
  @Override
  public void writeToFile(PrintWriter writer, ProtoFile protoFile, boolean outputComments) {

    writeLicenseAndWarning(writer, protoFile.getMetadata());

    writer.println("type: com.google.api.codegen.ConfigProto");
    writer.println("config_schema_version: 2.0.0");
    writer.println("language_settings:");
    // Consider adding other languages as well, if ever needed
    writer.println("  java:");
    writer.println("    package_name: com." + protoFile.getMetadata().getProtoPkg());
    writer.println("interfaces:");

    for (GrpcService service : protoFile.getServices().values()) {
      boolean firstMethod = true;

      for (GrpcMethod method : service.getMethods()) {
        Optional<Option> opt =
            method
                .getOptions()
                .stream()
                .filter(o -> o.getName().equals("google.cloud.operation_service"))
                .findFirst();

        if (!opt.isPresent()) {
          continue;
        }

        if (firstMethod) {
          writer.println(
              "- name: " + protoFile.getMetadata().getProtoPkg() + "." + service.getName());
          writer.println("  methods:");
          firstMethod = false;
        }

        writer.println("  - name: " + method.getName());
        // Consider making these configurable
        writer.println("    long_running:");
        writer.println("      initial_poll_delay_millis: 500");
        writer.println("      poll_delay_multiplier: 1.5");
        writer.println("      max_poll_delay_millis: 20000");
        writer.println("      total_poll_timeout_millis: 600000");
      }
    }
  }

  private void writeLicenseAndWarning(PrintWriter writer, ProtoFileMetadata metadata) {
    writer.println(metadata.getLicense().replaceAll("//", "#"));

    writer.println("# Generated by the disco-to-proto3-converter. DO NOT EDIT!");
    writer.println("# Source Discovery file: " + metadata.getDiscoFileName());
    writer.println("# Source file revision: " + metadata.getDiscoRevision());
    writer.println("# API name: " + metadata.getDiscoName());
    writer.println("# API version: " + metadata.getDiscoVersion());

    writer.println();
  }
}

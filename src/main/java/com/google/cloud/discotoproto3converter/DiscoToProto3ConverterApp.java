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
import java.util.Map;

public class DiscoToProto3ConverterApp extends ConverterApp {
  public DiscoToProto3ConverterApp() {
    super(new Proto3Writer());
  }

  /**
     Returns `text` with the suffix `target` changed to `replacement`.
   */
  public static String replaceSuffixWith(String text, String target, String replacement) {
    int start = text.lastIndexOf(target);
    StringBuilder builder = new StringBuilder();
    builder.append(text.substring(0, start));
    builder.append(replacement);
    return builder.toString();
  }

  /**
     Generates the proto file and, if the value of the argument "--output_file_path" ends in `.-`,
     also the gRPC service config and the GAPIC YAML config files.
   */
  public static void main(String[] args) throws IOException {
    String suffixForMultiFileGeneration = ".-";
    Map<String, String> parsedArgs = parseArgs(args);
    String outputFilePathArgument="--output_file_path";
    String outputFileStem = parsedArgs.get(outputFilePathArgument);

    if (!outputFileStem.endsWith(suffixForMultiFileGeneration)) {
      // only run the proto converter
      DiscoToProto3ConverterApp converterApp = new DiscoToProto3ConverterApp();
      converterApp.convert(parsedArgs);
      return;
    }

    // run all three converters

    System.err.print("\n\n\n*** generating protocol buffer file");
    parsedArgs.put(outputFilePathArgument,
        replaceSuffixWith(outputFileStem, suffixForMultiFileGeneration, ".proto"));
    DiscoToProto3ConverterApp protoConverterApp = new DiscoToProto3ConverterApp();
    protoConverterApp.convert(parsedArgs);

    System.err.print("\n\n\n*** generating grpc service config");
    parsedArgs.put(outputFilePathArgument,
        replaceSuffixWith(outputFileStem, suffixForMultiFileGeneration, "_grpc_service_config.json"));
    ServiceConfigGeneratorApp serviceConfigConverterApp = new ServiceConfigGeneratorApp();
    serviceConfigConverterApp.convert(parsedArgs);

    System.err.print("\n\n\n*** generating gapic yaml config");
    parsedArgs.put(outputFilePathArgument,
        replaceSuffixWith(outputFileStem, suffixForMultiFileGeneration, "_gapic.yaml"));
    GapicYamlGeneratorApp gapicYamlGeneratorApp = new GapicYamlGeneratorApp();
    gapicYamlGeneratorApp.convert(parsedArgs);
  }
}

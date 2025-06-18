/*
 * Copyright 2025 Google LLC
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

import java.io.IOException;
import java.util.Map;

public class DiscoveryConverterApp {

  public static void main(String[] args) throws IOException {
    Map<String, String> parsedArgs = ConverterApp.parseArgs(args);
    String outputFilePathArgument = "--output_file_path";
    String outputFileStem = parsedArgs.get(outputFilePathArgument);

    System.err.print("Generating protocol buffer file...");
    parsedArgs.put(outputFilePathArgument, outputFileStem.concat(".proto"));
    DiscoToProto3ConverterApp protoConverterApp = new DiscoToProto3ConverterApp();
    protoConverterApp.convert(parsedArgs);

    System.err.print("\nGenerating grpc service config...");
    parsedArgs.put(outputFilePathArgument, outputFileStem.concat("_grpc_service_config.json"));
    ServiceConfigGeneratorApp serviceConfigConverterApp = new ServiceConfigGeneratorApp();
    serviceConfigConverterApp.convert(parsedArgs);

    System.err.print("\nGenerating gapic yaml config...");
    parsedArgs.put(outputFilePathArgument, outputFileStem.concat("_gapic.yaml"));
    GapicYamlGeneratorApp gapicYamlGeneratorApp = new GapicYamlGeneratorApp();
    gapicYamlGeneratorApp.convert(parsedArgs);

    System.err.print("\nDone.\n");
  }
}

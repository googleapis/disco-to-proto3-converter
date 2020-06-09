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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.discotoproto3converter.disco.DiscoveryNode;
import com.google.cloud.discotoproto3converter.disco.Document;
import com.google.cloud.discotoproto3converter.proto3.DocumentToProtoConverter;
import com.google.cloud.discotoproto3converter.proto3.Proto3Writer;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class DiscoToProto3ConverterApp {

  public static void main(String[] args) {
    Map<String, String> parsedArgs = new HashMap<>();

    for (String arg : args) {
      String[] argNameVal = arg.split("=");
      parsedArgs.put(argNameVal[0], argNameVal[1]);
    }

    DiscoToProto3ConverterApp converter = new DiscoToProto3ConverterApp();
    converter.generateConfig(
        parsedArgs.get("--discovery_doc_path"),
        parsedArgs.get("--output_root_path"),
        parsedArgs.get("--output_file_name"));
  }

  private void generateConfig(String discoveryDocPath, String outputRoot, String outputFile) {
    try {
      Document document = createDocument(discoveryDocPath);
      DocumentToProtoConverter converter = new DocumentToProtoConverter(document);
      Proto3Writer writer = new Proto3Writer();
      writer.writeToFile(
          outputRoot,
          outputFile,
          converter.getPackageName(),
          converter.getAllMessages().values(),
          converter.getAllServices().values(),
          converter.getAllResourceOptions().values());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static Document createDocument(String discoveryDocPath) throws IOException {
    if (!new File(discoveryDocPath).exists()) {
      throw new FileNotFoundException("Discovery document filepath not found.");
    }

    Reader reader = new InputStreamReader(new FileInputStream(new File(discoveryDocPath)));
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(reader);
    return Document.from(new DiscoveryNode(root));
  }
}

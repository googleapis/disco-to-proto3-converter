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
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DiscoToProto3ConverterApp {

  public static void main(String[] args) throws IOException {
    Map<String, String> parsedArgs = new HashMap<>();

    // Optional Parameters
    parsedArgs.put("--service_ignorelist", "");
    parsedArgs.put("--message_ignorelist", "");

    for (String arg : args) {
      String[] argNameVal = arg.split("=");
      parsedArgs.put(argNameVal[0], argNameVal[1]);
    }

    DiscoToProto3ConverterApp converter = new DiscoToProto3ConverterApp();
    converter.convert(
        parsedArgs.get("--discovery_doc_path"),
        parsedArgs.get("--output_root_path"),
        parsedArgs.get("--output_file_name"),
        parsedArgs.get("--service_ignorelist"),
        parsedArgs.get("--message_ignorelist"));
  }

  public void convert(
      String discoveryDocPath,
      String outputRootPath,
      String outputFileName,
      String serviceIgnorelist,
      String messageIgnorelist)
      throws IOException {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Document document = app.createDocument(discoveryDocPath);
    DocumentToProtoConverter converter =
        new DocumentToProtoConverter(
            document,
            Paths.get(discoveryDocPath).getFileName().toString(),
            Arrays.asList(serviceIgnorelist.split(",")),
            Arrays.asList(messageIgnorelist.split(",")));
    String protoPkg = converter.getProtoFile().getProtoPkg();
    try (PrintWriter pw = app.makeDefaultDirsAndWriter(outputRootPath, outputFileName, protoPkg)) {
      Proto3Writer writer = new Proto3Writer();

      writer.writeToFile(
          pw,
          converter.getProtoFile(),
          converter.getAllMessages().values(),
          converter.getAllServices().values(),
          converter.getAllResourceOptions().values());
    }
  }

  PrintWriter makeDefaultDirsAndWriter(String outputDir, String fileName, String pkg)
      throws FileNotFoundException, UnsupportedEncodingException {
    Path outputPath = Paths.get(outputDir, pkg.replace('.', File.separatorChar));
    outputPath.toFile().mkdirs();
    String outputFilePath = Paths.get(outputPath.toString(), fileName).toString();
    return new PrintWriter(outputFilePath, "UTF-8");
  }

  Document createDocument(String discoveryDocPath) throws IOException {
    if (!new File(discoveryDocPath).exists()) {
      throw new FileNotFoundException("Discovery document filepath not found.");
    }

    Reader reader = new InputStreamReader(new FileInputStream(new File(discoveryDocPath)));
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(reader);
    return Document.from(new DiscoveryNode(root));
  }
}

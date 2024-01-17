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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.discotoproto3converter.disco.DiscoveryNode;
import com.google.cloud.discotoproto3converter.disco.Document;
import com.google.cloud.discotoproto3converter.proto3.ConverterWriter;
import com.google.cloud.discotoproto3converter.proto3.DocumentToProtoConverter;
import com.google.cloud.discotoproto3converter.proto3.ProtoFile;
import com.google.cloud.discotoproto3converter.proto3.ProtoMerger;
import com.google.cloud.discotoproto3converter.proto3.ProtoParser;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public abstract class ConverterApp {
  private final ConverterWriter writer;

  protected ConverterApp(ConverterWriter writer) {
    this.writer = writer;
  }

  // Note that serviceIgnoreList should contain the names of services as they would be naively
  // derived from the Discovery document (i.e. before disambiguation if they conflict with any of
  // the messages).
  public void convert(
      String discoveryDocPath,
      String previousProtoPath,
      String outputFilePath,
      String serviceIgnorelist,
      String messageIgnorelist,
      String relativeLinkPrefix,
      String enumsAsStrings,
      String outputComments)
      throws IOException {

    ProtoFile newProtoFile = null;
    if (discoveryDocPath != null) {
      Document document = createDocument(discoveryDocPath);
      DocumentToProtoConverter converter =
          new DocumentToProtoConverter(
              document,
              Paths.get(discoveryDocPath).getFileName().toString(),
              new HashSet<>(Arrays.asList(serviceIgnorelist.split(","))),
              new HashSet<>(Arrays.asList(messageIgnorelist.split(","))),
              relativeLinkPrefix,
              Boolean.valueOf(enumsAsStrings));
      newProtoFile = converter.getProtoFile();
    }

    ProtoFile previousProtoFile = null;
    if (previousProtoPath != null) {
      previousProtoFile = new ProtoParser(readProtoFile(previousProtoPath)).getProtoFile();
    }

    if (newProtoFile != null) {
      if (previousProtoFile != null) {
        new ProtoMerger().merge(newProtoFile, previousProtoFile);
      }
      try (PrintWriter pw = makeDefaultDirsAndWriter(outputFilePath)) {
        writer.writeToFile(pw, newProtoFile, Boolean.valueOf(outputComments));
      }
    } else if (previousProtoFile != null) {
      try (PrintWriter pw = makeDefaultDirsAndWriter(outputFilePath)) {
        writer.writeToFile(pw, previousProtoFile, Boolean.valueOf(outputComments));
      }
    }
  }

  public void convert(String[] args) throws IOException {
    Map<String, String> parsedArgs = parseArgs(args);
    convert(
        parsedArgs.get("--discovery_doc_path"),
        parsedArgs.get("--previous_proto_file_path"),
        parsedArgs.get("--output_file_path"),
        parsedArgs.get("--service_ignorelist"),
        parsedArgs.get("--message_ignorelist"),
        parsedArgs.get("--relative_link_prefix"),
        parsedArgs.get("--enums_as_strings"),
        parsedArgs.get("--output_comments"));
  }

  private Map<String, String> parseArgs(String[] args) {
    Map<String, String> parsedArgs = new HashMap<>();

    // Optional Parameters
    parsedArgs.put("--service_ignorelist", "");
    parsedArgs.put("--message_ignorelist", "");
    parsedArgs.put("--enums_as_strings", "false");
    parsedArgs.put("--output_comments", "true");

    for (String arg : args) {
      String[] argNameVal = arg.split("=");
      parsedArgs.put(argNameVal[0], argNameVal[1]);
    }

    return parsedArgs;
  }

  private PrintWriter makeDefaultDirsAndWriter(String outputFilePath)
      throws FileNotFoundException, UnsupportedEncodingException {
    Path outputPath = Paths.get(outputFilePath);
    outputPath.getParent().toFile().mkdirs();
    return new PrintWriter(outputFilePath, "UTF-8");
  }

  private Document createDocument(String discoveryDocPath) throws IOException {
    if (!new File(discoveryDocPath).exists()) {
      throw new FileNotFoundException("Discovery document filepath not found.");
    }

    Reader reader = new InputStreamReader(new FileInputStream(new File(discoveryDocPath)));
    ObjectMapper mapper = new ObjectMapper();
    JsonNode root = mapper.readTree(reader);
    return Document.from(new DiscoveryNode(root));
  }

  protected static String readProtoFile(String protoFilePath) throws IOException {
    return new String(Files.readAllBytes(Paths.get(protoFilePath)), StandardCharsets.UTF_8);
  }
}

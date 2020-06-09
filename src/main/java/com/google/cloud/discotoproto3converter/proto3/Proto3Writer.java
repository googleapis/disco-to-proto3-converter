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

package com.google.cloud.discotoproto3converter.proto3;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;

public class Proto3Writer {

  public void writeToFile(
      String outputDir,
      String fileName,
      String pkg,
      Collection<Message> messages,
      Collection<GrpcService> services,
      Collection<Option> resourceOptions) {

    Path outputPath = Paths.get(outputDir, pkg.replace('.', File.separatorChar));
    outputPath.toFile().mkdirs();
    String outputFilePath = Paths.get(outputPath.toString(), fileName).toString();

    try (PrintWriter writer = new PrintWriter(outputFilePath, "UTF-8")) {
      writer.println("syntax = \"proto3\";\n");

      writer.println("package " + pkg + ";\n");

      writer.println("import \"google/api/annotations.proto\";");
      writer.println("import \"google/api/resource.proto\";\n");

      // File level options
      writer.println("//");
      writer.println("// File level resource definitions");
      writer.println("//");
      printFileLevelOptions(resourceOptions, writer);

      // Messages
      writer.println("//");
      writer.println("// Messages");
      writer.println("//");
      printMessages(messages, writer, "");

      // Services
      writer.println("//");
      writer.println("// Services");
      writer.println("//");
      printServices(services, writer);

    } catch (FileNotFoundException | UnsupportedEncodingException e) {
      e.printStackTrace();
    }
  }

  private void printServices(Collection<GrpcService> services, PrintWriter writer) {
    for (GrpcService service : services) {
      writer.println("service " + service + " {");

      for (GrpcMethod method : service.getMethods()) {
        writer.println("  " + method + " {");

        StringBuilder optionsSb = new StringBuilder();
        for (Option option : method.getOptions()) {
          optionsSb.append("    option (").append(option).append(") = {\n");
          for (Map.Entry<String, String> prop : option.getProperties().entrySet()) {
            optionsSb.append("      " + prop.getKey() + ": " + '"' + prop.getValue() + "\"\n");
          }
          optionsSb.append("    };");
          writer.println(optionsSb);
        }

        writer.println("  }\n");
      }

      writer.println("}\n");
    }
  }

  private void printFileLevelOptions(Collection<Option> resourceOptions, PrintWriter writer) {
    for (Option resOption : resourceOptions) {
      writer.println("option (" + resOption + ") = {");
      for (Map.Entry<String, String> optProp : resOption.getProperties().entrySet()) {
        writer.println("  " + optProp.getKey() + ": " + '"' + optProp.getValue() + '"');
      }
      writer.println("};\n");
    }
  }

  private void printMessages(Collection<Message> messages, PrintWriter writer, String indent) {
    for (Message message : messages) {
      if ("ListPeeringRoutesNetworksRequest".equals(message.getName())) {
        int k = 0;
      }
      writer.println(indent + (message.isEnum() ? "enum " : "message ") + message + " {");

      printMessages(message.getEnums(), writer, indent + "  ");

      int fieldIndex = message.isEnum() ? 0 : 1;
      for (Field field : message.getFields()) {
        writer.print(indent + "  " + field.toString(fieldIndex++));

        String options = ";";
        if (!field.getOptions().isEmpty()) {
          StringBuilder optionsSb = new StringBuilder(" [\n");
          for (Option option : field.getOptions()) {
            optionsSb.append(indent).append("    (").append(option).append(") = {\n");
            for (Map.Entry<String, String> prop : option.getProperties().entrySet()) {
              optionsSb.append(indent).append("      ").append(prop.getKey()).append(": ")
                  .append('"').append(prop.getValue()).append("\"\n");
            }
            optionsSb.append(indent).append("    }");
          }
          optionsSb.append("\n").append(indent).append("  ];");
          options = optionsSb.toString();
        }

        writer.println(options);
      }
      writer.println(indent + "}\n");
    }
  }

}


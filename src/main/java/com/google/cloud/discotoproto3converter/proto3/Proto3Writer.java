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

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Proto3Writer {
  public void writeToFile(
      PrintWriter writer,
      ProtoFile protoFile,
      Collection<Message> messages,
      Collection<GrpcService> services,
      boolean printLroAnnotationDefinitions) {

    writeLicenseAndWarning(writer, protoFile);

    writer.println("syntax = \"proto3\";\n");

    writer.println("package " + protoFile.getProtoPkg() + ";\n");

    writer.println("import \"google/api/annotations.proto\";");
    writer.println("import \"google/api/client.proto\";");
    writer.println("import \"google/api/field_behavior.proto\";");
    writer.println("import \"google/api/resource.proto\";");

    if (printLroAnnotationDefinitions) {
      // LRO
      writer.println("import \"google/protobuf/descriptor.proto\";\n");
      printLroAnnotationDefinitions(printLroAnnotationDefinitions, writer);
    } else {
      writer.println();
    }

    // File Options
    writer.println("//");
    writer.println("// File Options");
    writer.println("//");
    printOptions(protoFile.getProtoPkg(), writer);

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
  }

  private void printLroAnnotationDefinitions(
      boolean printLroAnnotationDefinitions, PrintWriter writer) {
    writer.println("//");
    writer.println("// LRO Annotations");
    writer.println("//");
    writer.println("extend google.protobuf.FieldOptions {");
    writer.println("  OperationResponseMapping operation_field = 1149;");
    writer.println("  string operation_request_field = 1150;");
    writer.println("  string operation_response_field = 1151;");
    writer.println("}");
    writer.println("");
    writer.println("extend google.protobuf.MethodOptions {");
    writer.println("  string operation_service = 1249;");
    writer.println("  bool operation_polling_method = 1250;");
    writer.println("}");
    writer.println("");
    writer.println("enum OperationResponseMapping {");
    writer.println("  NAME = 0;");
    writer.println("  STATUS = 1;");
    writer.println("  ERROR_CODE = 2;");
    writer.println("  ERROR_MESSAGE = 3;");
    writer.println("}\n");
  }

  // TODO: refactor to use enum for option types
  // TODO: include helper method to build strings for options
  private void printOptions(String pkg, PrintWriter writer) {
    String[] tokens = pkg.split("\\.");
    List<String> capitalized =
        Arrays.stream(tokens).map(this::capitalize).collect(Collectors.toList());

    writer.println("option csharp_namespace = \"" + String.join(".", capitalized) + "\";");
    String goPkg1 = Arrays.stream(tokens).skip(1).collect(Collectors.joining("/"));
    String goPkg2 = tokens[tokens.length - 2];
    String goPkg = "google.golang.org/genproto/googleapis/" + goPkg1 + ";" + goPkg2;
    writer.println("option go_package = \"" + goPkg + "\";");
    writer.println("option java_multiple_files = true;");
    writer.println("option java_package = \"" + "com." + pkg + "\";");
    writer.println("option php_namespace = \"" + String.join("\\\\", capitalized) + "\";");
    writer.println("option ruby_package = \"" + String.join("::", capitalized) + "\";\n");
  }

  private String capitalize(String name) {
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private void printServices(Collection<GrpcService> services, PrintWriter writer) {
    for (GrpcService service : services) {
      writer.println(formatDescription("", service.getDescription()));
      writer.println("service " + service + " {");

      for (Option opt : service.getOptions()) {
        // Support only scalar service-level options for now (there are not use-cases for vector
        // ones).
        String comaSeparatedScalar = (String) opt.getProperties().get("");
        if (comaSeparatedScalar == null) {
          continue;
        }
        writer.println("  option (" + opt.getName() + ") =");

        String[] comaSeparatedComponents = comaSeparatedScalar.split(",");
        for (int i = 0; i < comaSeparatedComponents.length; i++) {
          writer.print("    \"" + comaSeparatedComponents[i]);
          writer.println(i >= comaSeparatedComponents.length - 1 ? "\";" : ",\"");
        }
        writer.println();
      }

      for (GrpcMethod method : service.getMethods()) {
        writer.println(formatDescription("  ", method.getDescription()));
        writer.println("  " + method + " {");

        for (Option option : method.getOptions()) {
          StringBuilder optionsSb = new StringBuilder();
          optionsSb.append("    option (").append(option).append(") = ");
          Object scalarOptionValue = option.getProperties().get("");
          if (option.getProperties().size() == 1 && scalarOptionValue != null) {
            optionsSb.append(optionValueToString(scalarOptionValue)).append(";");
          } else {
            optionsSb.append("{\n");
            for (Map.Entry<String, Object> prop : option.getProperties().entrySet()) {
              optionsSb
                  .append("      ")
                  .append(prop.getKey())
                  .append(": ")
                  .append(optionValueToString(prop.getValue()))
                  .append("\n");
            }
            optionsSb.append("    };");
          }

          writer.println(optionsSb);
        }

        writer.println("  }\n");
      }

      writer.println("}\n");
    }
  }

  private void printMessages(Collection<Message> messages, PrintWriter writer, String indent) {
    for (Message message : messages) {
      writer.println(formatDescription(indent, message.getDescription()));
      writer.println(indent + (message.isEnum() ? "enum " : "message ") + message + " {");

      printMessages(message.getEnums(), writer, indent + "  ");

      for (Map.Entry<Integer, Field> entry : message.getFieldsWithNumbers().entrySet()) {
        Field field = entry.getValue();
        int fieldIndex = entry.getKey();
        if (field.getDescription() != null && !field.getDescription().isEmpty()) {
          writer.println(formatDescription(indent + "  ", field.getDescription()));
        }
        writer.print(indent + "  " + field.toString(fieldIndex));

        String options = ";";
        if (!field.getOptions().isEmpty()) {
          StringBuilder optionsSb = new StringBuilder();
          for (Option option : field.getOptions()) {
            optionsSb.append(optionsSb.length() <= 0 ? " [" : ",");
            if (field.getOptions().size() > 1) {
              optionsSb.append("\n    ");
            }

            // Only scalar field level options are supported as of now.
            Object scalarOptionValue = option.getProperties().get("");
            if (option.getProperties().size() == 1 && scalarOptionValue != null) {
              optionsSb
                  .append("(")
                  .append(option)
                  .append(") = ")
                  .append(optionValueToString(scalarOptionValue));
            }
          }
          if (field.getOptions().size() > 1) {
            optionsSb.append("\n  ");
          }
          optionsSb.append("];");
          options = optionsSb.toString();
        }

        writer.println(options);
        writer.println();
      }
      writer.println(indent + "}\n");
    }
  }

  private Object optionValueToString(Object optionValue) {
    return (optionValue instanceof String) ? "\"" + optionValue + "\"" : optionValue;
  }

  private String formatDescription(String prefix, String description) {
    if (description == null || description.isEmpty()) {
      return prefix + "//";
    }

    String comments = description.replace("\n", "\n" + prefix + "// ");
    // This is to get rid of end of line whitespaces, which are often removed by text editors
    // automatically.
    comments = comments.replaceAll(" *\n", "\n");
    return prefix + "// " + comments;
  }

  private void writeLicenseAndWarning(PrintWriter writer, ProtoFile protoFile) {
    writer.println(protoFile.getLicense());

    writer.println("// Generated by the disco-to-proto3-converter. DO NOT EDIT!");
    writer.println("// Source Discovery file: " + protoFile.getDiscoFileName());
    writer.println("// Source file revision: " + protoFile.getDiscoRevision());
    writer.println("// API name: " + protoFile.getDiscoName());
    writer.println("// API version: " + protoFile.getDiscoVersion());

    writer.println();
  }
}

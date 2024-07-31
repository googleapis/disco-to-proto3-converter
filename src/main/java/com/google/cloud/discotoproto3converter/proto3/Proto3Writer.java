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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Proto3Writer implements ConverterWriter {
  private static final String GO_CLOUD_PREFIX = "google.cloud.";

  private static final Pattern SEMANTIC_VERSION_REGEX_PATTERN =
      Pattern.compile(
          "^(?<majorversion>v?"
              + "(?<majornumber>\\d+)"
              + "((?<releaselevelname>[a-zA-Z_]+)"
              + "(?<releaselevelnumber>[0-9]*)"
              + "(?<releaseleveltrailing>[a-zA-Z_]\\w*)?)?)"
              + "(\\.\\d+){0,2}$");

  @Override
  public void writeToFile(PrintWriter writer, ProtoFile protoFile, boolean outputComments) {

    ProtoFileMetadata metadata = protoFile.getMetadata();
    writeLicenseAndWarning(writer, metadata);

    writer.println("syntax = \"proto3\";\n");

    writer.println("package " + metadata.getProtoPkg() + ";\n");

    writer.println("import \"google/api/annotations.proto\";");
    writer.println("import \"google/api/client.proto\";");
    writer.println("import \"google/api/field_behavior.proto\";");
    writer.println("import \"google/api/resource.proto\";");

    if (protoFile.isHasLroDefinitions()) {
      writer.println("import \"google/cloud/extended_operations.proto\";");
    }

    if (protoFile.HasAnyFields()) {
      writer.println("import \"google/protobuf/any.proto\";");
    }
    if (protoFile.UsesStructProto()) {
      writer.println("import \"google/protobuf/struct.proto\";");
    }

    writer.println();

    // File Options
    writer.println("//");
    writer.println("// File Options");
    writer.println("//");
    printOptions(metadata.getProtoPkg(), writer);

    // Messages
    writer.println("//");
    writer.println("// Messages");
    writer.println("//");
    printMessages(protoFile.getMessages().values(), writer, "", outputComments);

    // Services
    writer.println("//");
    writer.println("// Services");
    writer.println("//");
    printServices(protoFile.getServices().values(), writer, outputComments);
  }

  // TODO: refactor to use enum for option types
  // TODO: include helper method to build strings for options
  private void printOptions(String pkg, PrintWriter writer) {
    String[] tokens = pkg.split("\\.");
    List<String> capitalized =
        Arrays.stream(tokens).map(this::capitalize).collect(Collectors.toList());

    List<String> csharpCapitalized =
        Arrays.stream(String.join(".", capitalized).split("(?<=\\d)|(?<=\\d\\d)|(?<=\\d\\d\\d)"))
            .map(this::capitalize)
            .collect(Collectors.toList());

    writer.println("option csharp_namespace = \"" + String.join("", csharpCapitalized) + "\";");
    writer.println("option go_package = \"" + getGoPackage(pkg) + "\";");
    writer.println("option java_multiple_files = true;");
    writer.println("option java_package = \"" + "com." + pkg + "\";");
    writer.println("option php_namespace = \"" + String.join("\\\\", capitalized) + "\";");
    writer.println("option ruby_package = \"" + String.join("::", capitalized) + "\";\n");
  }

  private String getGoPackage(String protoPkg) {
    String[] segments;
    if (protoPkg.startsWith(GO_CLOUD_PREFIX)) {
      segments = protoPkg.substring(GO_CLOUD_PREFIX.length()).split("\\.");
    } else {
      segments = protoPkg.substring(protoPkg.indexOf('.', 0)).split("\\.");
    }

    int pkgNameIndex = segments.length - 1;
    for (int i = 0; i < segments.length; i++) {
      if (SEMANTIC_VERSION_REGEX_PATTERN.matcher(segments[i]).matches()) {
        segments[i] = "api" + segments[i];
        if (pkgNameIndex == i) {
          pkgNameIndex--;
        }
        break;
      }
    }
    String goImportPath = String.join("/", segments);
    return String.format(
        "cloud.google.com/go/%s/%spb;%spb",
        goImportPath, segments[pkgNameIndex], segments[pkgNameIndex]);
  }

  private String capitalize(String name) {
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  private void printServices(
      Collection<GrpcService> services, PrintWriter writer, boolean outputComments) {
    for (GrpcService service : services) {
      if (outputComments) {
        writer.println(formatDescription("", service.getDescription()));
      }
      writer.println("service " + service + " {");

      for (Option opt : service.getOptions()) {
        // Support only scalar service-level options for now (there are not use-cases
        // for vector
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
        if (outputComments) {
          writer.println(formatDescription("  ", method.getDescription()));
        }
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

  private void printMessages(
      Collection<Message> messages, PrintWriter writer, String indent, boolean outputComments) {
    for (Message message : messages) {
      if (outputComments) {
        writer.println(formatDescription(indent, message.getDescription()));
      }
      writer.println(indent + (message.isEnum() ? "enum " : "message ") + message + " {");

      printMessages(message.getEnums(), writer, indent + "  ", outputComments);

      for (Map.Entry<Integer, Field> entry : message.getFieldsWithNumbers().entrySet()) {
        Field field = entry.getValue();
        int fieldIndex = entry.getKey();
        if (field.getDescription() != null && !field.getDescription().isEmpty()) {
          if (outputComments) {
            writer.println(formatDescription(indent + "  ", field.getDescription()));
          }
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
    // This is to get rid of end of line whitespaces, which are often removed by
    // text editors
    // automatically.
    comments = comments.replaceAll(" *\n", "\n");
    return prefix + "// " + comments;
  }

  private void writeLicenseAndWarning(PrintWriter writer, ProtoFileMetadata met) {
    writer.println(met.getLicense());

    writer.println("// Generated by the disco-to-proto3-converter. DO NOT EDIT!");
    writer.println("// Source Discovery file: " + met.getDiscoFileName());
    writer.println("// Source file revision: " + met.getDiscoRevision());
    writer.println("// API name: " + met.getDiscoName());
    writer.println("// API version: " + met.getDiscoVersion());

    writer.println();
  }
}

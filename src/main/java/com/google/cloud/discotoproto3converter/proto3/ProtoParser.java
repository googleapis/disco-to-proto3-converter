/*
 * Copyright 2022 Google LLC
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

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtoParser {
  private static final Pattern DISCO_FILE_NAME_PATTERN =
      Pattern.compile("^// [\\w\\s]+file:\\s+(?<fileName>[\\w\\.]+)", Pattern.MULTILINE);
  private static final Pattern DISCO_API_NAME_PATTERN =
      Pattern.compile("^// [\\w\\s]+name:\\s+(?<apiName>\\w+)", Pattern.MULTILINE);
  private static final Pattern DISCO_VERSION_PATTERN =
      Pattern.compile("^// [\\w\\s]+version:\\s+(?<apiVersion>\\w+)", Pattern.MULTILINE);
  private static final Pattern DISCO_REVISION_PATTERN =
      Pattern.compile("^// [\\w\\s]+revision:\\s+(?<revision>\\d+)", Pattern.MULTILINE);
  private static final Pattern PROTO_PKG_PATTERN =
      Pattern.compile("^package\\s+(?<protoPkg>[\\w.]+)\\s*;", Pattern.MULTILINE);

  private static final Pattern MESSAGE_PATTERN =
      Pattern.compile("^(?<message>message|enum)\\s+(?<name>\\w+)");
  private static final Pattern MESSAGE_FIELD_PATTERN =
      Pattern.compile(
          "^(?<optional>optional)?(?<repeated>repeated)?(map<(?<keyType>\\w+)\\s*,)?\\s*(?<type>\\w+)\\s*>?\\s+(?<name>\\w+)");
  private static final Pattern FIELD_OPTIONS_PATTERN =
      Pattern.compile(
          "^\\s*\\(\\s*(?<name>[\\w.]+)\\s*\\)\\s*=\\s*(?<quotes>[\"'])?(?<value>[\\w{/\\\\}\\-.=*~,:]+)[\"']?\\s*$");
  private static final Pattern ENUM_FIELD_PATTERN =
      Pattern.compile("(?<name>\\w+)\\s*=\\s*(?<number>\\d+)");

  private static final Pattern SERVICE_PATTERN =
      Pattern.compile("^\\s*service\\s*(?<name>\\w+)\\s*\\{");
  private static final Pattern RPC_PATTERN =
      Pattern.compile(
          "^\\s*rpc\\s+(?<name>\\w+)\\(\\s*(?<input>\\w+)\\s*\\)\\s*returns\\s*\\((?<output>\\w+)\\)\\s*\\{");
  private static final Pattern RPC_OPTION_NAME_PATTERN =
      Pattern.compile("^\\s*option\\s+\\(\\s*(?<name>[\\w.]+)\\s*\\)");

  private static final Pattern RPC_MAP_OPTION_VALUE_PATTERN =
      Pattern.compile("(?<key>\\w+)\\s*:\\s*[\"'](?<value>[\\w{/\\\\}\\-.=*~,:]+)[\"']+");
  private static final Pattern RPC_STRING_OPTION_VALUE_PATTERN =
      Pattern.compile("[\"'](?<value>[\\w{/\\\\}\\-.=*~,:]+)[\"']\\s*");

  private final ProtoFile protoFile = new ProtoFile();

  public ProtoParser(String content) {
    this.protoFile.setMetadata(parseProtoFileMetadata(content));
    String normalizedContent = normalizeContent(content);
    parseMessages(normalizedContent);
    for (Message message : protoFile.getMessages().values()) {
      resolveReferences(message);
    }
    parseServices(normalizedContent);
  }

  private String normalizeContent(String content) {
    String normalizedContent = content;

    // 1) Remove comments
    normalizedContent = normalizedContent.replaceAll("(?m)(^\\s*//.*)", "");
    // 2) Remove new lines
    normalizedContent = normalizedContent.replaceAll("(?m)(\\n+)", "\n");
    // 3) Remove extra whitespaces
    normalizedContent = normalizedContent.replaceAll("(?m)[\\s]\\s+", "");
    // 4) Make sure closing message/service curly bracket is the sole character on aline (1)
    normalizedContent = normalizedContent.replaceAll("(?m)(;})(\\w)", "$1\n$2");
    // 5) Make sure message/service opening curly bracket is at the end of a line
    normalizedContent = normalizedContent.replaceAll("(?m)((\\)|\\w) \\{)", "$1\n");
    // 6) Make sure closing message/service curly bracket is the sole character on a line (2)
    normalizedContent = normalizedContent.replaceAll("(?m);}", ";\n}");
    // 7) Make sure there is one rpc per line
    normalizedContent = normalizedContent.replaceAll("(?m);rpc ", ";\nrpc ");

    return normalizedContent;
  }

  public ProtoFile getProtoFile() {
    return protoFile;
  }

  private ProtoFileMetadata parseProtoFileMetadata(String content) {
    Matcher m = DISCO_FILE_NAME_PATTERN.matcher(content);
    String discoFileName = m.find() ? m.group("fileName") : null;
    m = DISCO_API_NAME_PATTERN.matcher(content);
    String discoName = m.find() ? m.group("apiName") : null;
    m = DISCO_VERSION_PATTERN.matcher(content);
    String discoVersion = m.find() ? m.group("apiVersion") : null;
    m = DISCO_REVISION_PATTERN.matcher(content);
    String discoRevision = m.find() ? m.group("revision") : null;
    m = PROTO_PKG_PATTERN.matcher(content);
    String protoPkg = m.find() ? m.group("protoPkg") : null;

    return new ProtoFileMetadata(
        discoFileName, discoName, discoVersion, discoRevision, protoPkg, discoVersion);
  }

  private void parseMessages(String conent) {
    Scanner scanner = new Scanner(conent);
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      Message message = parseMessage(scanner, line);
      if (message != null) {
        protoFile.getMessages().put(message.getName(), message);
      }
    }
    scanner.close();
  }

  private void resolveReferences(Message message) {
    for (Field field : message.getFields()) {
      Message valueType = field.getValueType();
      if (valueType.isRef()) {
        field.setValueType(protoFile.getMessages().get(valueType.getName()));
      } else {
        resolveReferences(valueType);
      }
    }
  }

  private Message parseMessage(Scanner scanner, String line) {
    Matcher m = MESSAGE_PATTERN.matcher(line);
    if (!m.find()) {
      return null;
    }
    boolean isEnum = "enum".equals(m.group("message"));
    Message message = new Message(m.group("name"), false, isEnum, null);

    String nextLine;
    SortedMap<String, Message> nestedEnums = new TreeMap<>();
    do {
      nextLine = scanner.nextLine().trim();
      Message enm = parseMessage(scanner, nextLine);
      if (enm != null) {
        message.getEnums().add(enm);
        nestedEnums.put(enm.getName(), enm);
      } else {
        message.getFields().addAll(parseFields(nextLine, isEnum, nestedEnums));
      }
    } while (scanner.hasNextLine() && !"}".equals(nextLine));
    return message;
  }

  private SortedSet<Field> parseFields(
      String line, boolean enumFields, SortedMap<String, Message> nestedEnums) {
    String[] fieldStrings = line.split(";");
    SortedSet<Field> fields = new TreeSet<>();
    for (String fieldString : fieldStrings) {
      Field f = enumFields ? parseEnumField(fieldString) : parseField(fieldString, nestedEnums);
      if (f != null) {
        fields.add(f);
      }
    }

    return fields;
  }

  private Field parseEnumField(String fieldString) {
    Message emptyType = Message.PRIMITIVES.get("");

    Matcher m = ENUM_FIELD_PATTERN.matcher(fieldString);
    if (!m.find()) {
      return null;
    }
    return new Field(
        m.group("name"), emptyType, false, false, null, null, "0".equals(m.group("number")));
  }

  private Field parseField(String fieldString, SortedMap<String, Message> nestedEnums) {
    String[] components = fieldString.split("\\[", 2);

    String fieldDefinition = components[0];
    Matcher m = MESSAGE_FIELD_PATTERN.matcher(fieldDefinition);

    if (!m.find()) {
      return null;
    }
    String typeName = m.group("type");
    Message valueType = Message.PRIMITIVES.get(typeName);
    if (valueType == null) {
      valueType = nestedEnums.get(typeName);
      if (valueType == null) {
        valueType = new Message(typeName, true, false, null);
      }
    }
    Message keyType = Message.PRIMITIVES.get(m.group("keyType"));
    boolean repeated = m.group("repeated") != null || keyType != null;
    boolean optional = m.group("optional") != null;

    Field parsedField =
        new Field(m.group("name"), valueType, repeated, optional, keyType, null, false);

    if (components.length > 1) {
      parsedField
          .getOptions()
          .addAll(
              parseFieldOptions(components[1].substring(0, components[1].lastIndexOf(']')).trim()));
    }

    return parsedField;
  }

  private List<Option> parseFieldOptions(String line) {
    String[] options = line.split(",");
    List<Option> parsedOptions = new ArrayList<>();
    for (String option : options) {
      Matcher m = FIELD_OPTIONS_PATTERN.matcher(option);
      if (!m.find()) {
        continue;
      }

      Option opt = new Option(m.group("name"));
      String value = m.group("value");
      opt.getProperties()
          .put("", m.group("quotes") == null ? ProtoOptionValues.valueOf(value) : value);
      parsedOptions.add(opt);
    }

    return parsedOptions;
  }

  private void parseServices(String conent) {
    Scanner scanner = new Scanner(conent);
    while (scanner.hasNextLine()) {
      String line = scanner.nextLine();
      GrpcService service = parseService(scanner, line);
      if (service != null) {
        protoFile.getServices().put(service.getName(), service);
      }
    }
    scanner.close();
  }

  private GrpcService parseService(Scanner scanner, String line) {
    Matcher m = SERVICE_PATTERN.matcher(line);
    if (!m.find()) {
      return null;
    }
    GrpcService service = new GrpcService(m.group("name"), null);

    String nextLine;
    do {
      nextLine = scanner.nextLine().trim();
      GrpcMethod rpc = parseRpc(scanner, nextLine);
      if (rpc != null) {
        service.getMethods().add(rpc);
      } else {
        service.getOptions().addAll(parseServiceAndRpcOptions(nextLine));
      }
    } while (scanner.hasNextLine() && !"}".equals(nextLine));

    return service;
  }

  private GrpcMethod parseRpc(Scanner scanner, String line) {
    Matcher m = RPC_PATTERN.matcher(line);
    if (!m.find()) {
      return null;
    }

    GrpcMethod rpc =
        new GrpcMethod(
            m.group("name"),
            protoFile.getMessages().get(m.group("input")),
            protoFile.getMessages().get(m.group("output")),
            null);

    String nextLine;
    do {
      nextLine = scanner.nextLine().trim();
      rpc.getOptions().addAll(parseServiceAndRpcOptions(nextLine));
    } while (scanner.hasNextLine() && !"}".equals(nextLine));

    return rpc;
  }

  private List<Option> parseServiceAndRpcOptions(String line) {
    List<Option> parsedOptions = new ArrayList<>();
    if (!line.startsWith("option")) {
      return parsedOptions;
    }
    String[] optionStrings = line.split(";");

    for (String optionString : optionStrings) {
      parsedOptions.add(parseServiceAndRpcOption(optionString));
    }
    return parsedOptions;
  }

  private Option parseServiceAndRpcOption(String optionString) {
    String[] components = optionString.split("=", 2);

    Matcher m = RPC_OPTION_NAME_PATTERN.matcher(components[0]);
    if (!m.find()) {
      return null;
    }

    Option option = new Option(m.group("name"));
    if ("google.cloud.operation_service".equals(option.getName())) {
      protoFile.setHasLroDefinitions(true);
    }

    String optionValue = components[1].trim();
    if (optionValue.endsWith("}")) {
      m = RPC_MAP_OPTION_VALUE_PATTERN.matcher(optionValue);
      while (m.find()) {
        option.getProperties().put(m.group("key"), m.group("value"));
      }
    } else if (optionValue.startsWith("\"")) {
      m = RPC_STRING_OPTION_VALUE_PATTERN.matcher(optionValue);
      StringBuilder sb = new StringBuilder();
      while (m.find()) {
        sb.append(m.group("value"));
      }
      option.getProperties().put("", sb.toString());
    } else if (optionValue.equals("true")) {
      option.getProperties().put("", true);
    } else if (optionValue.equals("false")) {
      option.getProperties().put("", false);
    }

    return option;
  }
}

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
package com.google.cloud.discotoproto3converter.disco;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Adapted from its counterpart in gapic-generator.
 *
 * <p>A representation of a Discovery Document schema.
 *
 * <p>Note that this class is not necessarily a 1-1 mapping of the official specification.
 */
@AutoValue
public abstract class Schema implements Node {
  /**
   * Returns a schema constructed from root, or an empty schema if root has no children.
   *
   * @param root the root node to parse.
   * @param key in the parent node's schema map, the key that maps to this schema.
   * @param parent the parent of this schema.
   * @return a schema.
   */
  public static Schema from(DiscoveryNode root, String key, Node parent) {
    if (root.isEmpty()) {
      return empty();
    }
    Schema additionalProperties = Schema.from(root.getObject("additionalProperties"), "", null);
    if (additionalProperties.type() == Type.EMPTY && additionalProperties.reference().isEmpty()) {
      additionalProperties = null;
    }
    String defaultValue = root.getString("default");
    String description = root.getString("description");
    Format format = Format.getEnum(root.getString("format"));
    String id = root.getString("id");

    DiscoveryNode enumNode = root.getArray("enum");
    boolean isEnum = !enumNode.isEmpty();
    ImmutableList.Builder<String> enumValues = ImmutableList.builder();
    ImmutableList.Builder<String> enumDescriptions = ImmutableList.builder();
    if (isEnum) {
      for (DiscoveryNode enumElement : enumNode.getElements()) {
        enumValues.add(enumElement.asText());
      }
      DiscoveryNode enumDescNode = root.getArray("enumDescriptions");
      for (DiscoveryNode enumDescElement : enumDescNode.getElements()) {
        enumDescriptions.add(enumDescElement.asText());
      }
    }

    Schema items = Schema.from(root.getObject("items"), key, null);
    if (items.type() == Type.EMPTY && items.reference().isEmpty()) {
      items = null;
    }
    String location = root.getString("location");
    String pattern = root.getString("pattern");

    ImmutableMap.Builder<String, Schema> propertiesBuilder = ImmutableMap.builder();
    DiscoveryNode propertiesNode = root.getObject("properties");
    for (String name : propertiesNode.getFieldNames()) {
      propertiesBuilder.put(name, Schema.from(propertiesNode.getObject(name), name, null));
    }
    ImmutableMap<String, Schema> properties = propertiesBuilder.build();

    String reference = root.getString("$ref");
    boolean repeated = root.getBoolean("repeated");
    boolean required = root.getBoolean("required");
    Type type = Type.getEnum(root.getString("type"));

    // additionalProperties is a dynamically-keyed map in Discovery docs.
    boolean isMap = additionalProperties != null;

    Schema thisSchema =
        Schema.newBuilder()
            .setAdditionalProperties(additionalProperties)
            .setDefaultValue(defaultValue)
            .setDescription(description)
            .setFormat(format)
            .setId(id)
            .setIsEnum(isEnum)
            .setEnumValues(enumValues.build())
            .setEnumDescriptions(enumDescriptions.build())
            .setIsMap(isMap)
            .setItems(items)
            .setKey(key)
            .setLocation(location)
            .setPattern(pattern)
            .setProperties(properties)
            .setReference(reference)
            .setRepeated(repeated)
            .setRequired(required)
            .setType(type)
            .build();
    thisSchema.parent = parent;
    if (items != null) {
      items.setParent(thisSchema);
    }
    for (Schema schema : properties.values()) {
      schema.setParent(thisSchema);
    }
    if (additionalProperties != null) {
      additionalProperties.setParent(thisSchema);
    }

    return thisSchema;
  }

  /** @return a non-null identifier for this schema. */
  public String getIdentifier() {
    return Strings.isNullOrEmpty(id()) ? key() : id();
  }

  public static Schema empty() {
    return Schema.newBuilder()
        .setFormat(Format.EMPTY)
        .setType(Type.EMPTY)
        .setId("")
        .setKey("")
        .setIsEnum(false)
        .setRequired(false)
        .setRepeated(false)
        .setIsMap(false)
        .build();
  }

  @JsonIgnore @Nullable private Node parent;

  /** @return the {@link Node} that contains this Schema. */
  @Nullable
  public Node parent() {
    return parent;
  }

  void setParent(Node parent) {
    this.parent = parent;
  }

  /** @return the schema of the additionalProperties, or null if none. */
  @Nullable
  public abstract Schema additionalProperties();

  /** @return the default value. */
  public abstract String defaultValue();

  /** @return the description. */
  public abstract String description();

  /** @return the format. */
  public abstract Format format();

  /** @return the ID. */
  public abstract String id();

  /** @return whether or not the schema is an enum. */
  public abstract boolean isEnum();

  public abstract List<String> enumValues();

  public abstract List<String> enumDescriptions();

  /**
   * @return the schema for each element in the array if this schema is an array, or null if not.
   */
  @Nullable
  public abstract Schema items();

  /** @return the key that this object's parent uses to map to this Schema. */
  public abstract String key();

  /** @return the location. */
  public abstract String location();

  /** @return the pattern. */
  public abstract String pattern();

  /**
   * @return the map of property names to schemas, in the same order they are defined in the
   *     Discovery document.
   */
  public abstract ImmutableMap<String, Schema> properties();

  /** @return the reference. */
  public abstract String reference();

  /** @return whether or not the schema is repeated. */
  public abstract boolean repeated();

  /** @return whether or not the schema is required. */
  public abstract boolean required();

  /** @return whether or not the schema is a map. */
  public abstract boolean isMap();

  public boolean isRepeated() {
    return type() == Type.ARRAY;
  }

  /** @return the type. */
  public abstract Type type();

  /** The set of types a schema can represent. */
  public enum Type {
    ANY("any"),
    ARRAY("array"),
    BOOLEAN("boolean"),
    EMPTY(""),
    INTEGER("integer"),
    NUMBER("number"),
    OBJECT("object"),
    STRING("string");

    private String text;

    Type(String text) {
      this.text = text;
    }

    /**
     * @param text the JSON text of the type.
     * @return the enum representing the raw JSON type.
     */
    public static Type getEnum(String text) {
      for (Type t : values()) {
        if (t.text.equals(text)) {
          return t;
        }
      }
      throw new IllegalArgumentException("unknown type: " + text);
    }
  }

  /** The set of formats a schema can represent. */
  public enum Format {
    BYTE("byte"),
    DATE("date"),
    DATETIME("date-time"),
    DOUBLE("double"),
    EMPTY(""),
    FLOAT("float"),
    INT32("int32"),
    INT64("int64"),
    UINT32("uint32"),
    UINT64("uint64"),
    FIXED32("fixed32"),
    FIXED64("fixed64"),
    // standard protobuf types:
    ANY("google.protobuf.Any"),
    LISTVALUE("google.protobuf.ListValue"),
    STRUCT("google.protobuf.Struct"),
    VALUE("google.protobuf.Value");

    private String text;

    Format(String text) {
      this.text = text;
    }

    public String toString() {
      return this.text;
    }

    /**
     * @param text the JSON text of the format.
     * @return the enum representing the raw JSON format.
     */
    public static Format getEnum(String text) {
      if (text.isEmpty()) {
        return EMPTY;
      }
      for (Format f : values()) {
        if (f.text.equals(text)) {
          return f;
        }
      }
      // Unexpected formats are ignored.
      return EMPTY;
    }
  }

  public boolean isPathParam() {
    return location().equals("path");
  }

  @Override
  public String toString() {
    return String.format("Schema \"%s\", type %s", getIdentifier(), type());
  }

  /**
   * @return hashCode that should be unique for each underlying Node in the Document. This function
   *     includes the location of the node in its calculation, so two different nodes with the same
   *     content but different parents will still have different hashCodes.
   */
  @Override
  public int hashCode() {
    return Objects.hash(
        additionalProperties() == null ? null : additionalProperties().getIdentifier(),
        defaultValue(),
        description(),
        format(),
        id(),
        isEnum(),
        items() == null ? null : items().getIdentifier(),
        key(),
        location(),
        pattern(),
        parent != null ? parent.id() : "",
        properties().keySet(),
        reference(),
        repeated(),
        required(),
        type());
  }

  public int contentHashCode() {
    return Objects.hash(
        additionalProperties() == null ? null : additionalProperties().getIdentifier(),
        defaultValue(),
        // we explicitly exclude description()
        format(),
        id(),
        isEnum(),
        items() == null ? null : items().getIdentifier(),
        key(),
        location(),
        pattern(),
        properties().keySet(),
        reference(),
        repeated(),
        required(),
        type());
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof Schema)) {
      return false;
    }
    Schema schema2 = (Schema) other;

    return Objects.equals(
            additionalProperties() == null ? null : additionalProperties().getIdentifier(),
            schema2.additionalProperties() == null
                ? null
                : schema2.additionalProperties().getIdentifier())
        && Objects.equals(defaultValue(), schema2.defaultValue())
        && Objects.equals(description(), schema2.description())
        && Objects.equals(format(), schema2.format())
        && Objects.equals(id(), schema2.id())
        && Objects.equals(isEnum(), schema2.isEnum())
        && Objects.equals(
            items() == null ? null : items().getIdentifier(),
            schema2.items() == null ? null : schema2.items().getIdentifier())
        && Objects.equals(key(), schema2.key())
        && Objects.equals(location(), schema2.location())
        && Objects.equals(pattern(), schema2.pattern())
        && Objects.equals(
            parent != null ? parent.id() : "", schema2.parent != null ? schema2.parent.id() : "")
        && Objects.equals(properties().keySet(), schema2.properties().keySet())
        && Objects.equals(reference(), schema2.reference())
        && Objects.equals(repeated(), schema2.repeated())
        && Objects.equals(required(), schema2.required())
        && Objects.equals(type(), schema2.type());
  }

  public static Builder newBuilder() {
    return new AutoValue_Schema.Builder()
        .setDefaultValue("")
        .setDescription("")
        .setLocation("")
        .setPattern("")
        .setProperties(ImmutableMap.of())
        .setEnumValues(ImmutableList.of())
        .setEnumDescriptions(ImmutableList.of())
        .setReference("");
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setAdditionalProperties(Schema val);

    public abstract Builder setDefaultValue(String val);

    public abstract Builder setDescription(String val);

    public abstract Builder setFormat(Format val);

    public abstract Builder setId(String val);

    public abstract Builder setIsEnum(boolean val);

    public abstract Builder setEnumValues(List<String> val);

    public abstract Builder setEnumDescriptions(List<String> val);

    public abstract Builder setIsMap(boolean val);

    public abstract Builder setItems(Schema val);

    public abstract Builder setKey(String val);

    public abstract Builder setLocation(String val);

    public abstract Builder setPattern(String val);

    public abstract Builder setProperties(ImmutableMap<String, Schema> val);

    public abstract Builder setReference(String val);

    public abstract Builder setRepeated(boolean val);

    public abstract Builder setRequired(boolean val);

    public abstract Builder setType(Type val);

    public abstract Schema build();
  }
}

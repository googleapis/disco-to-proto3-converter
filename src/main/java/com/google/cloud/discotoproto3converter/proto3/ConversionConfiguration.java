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
package com.google.cloud.discotoproto3converter.proto3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class ConversionConfiguration {

  /* Only these fields are exposed in the external proto config. Other fields defined later in this
   * file are used in the course of processing the config.*/

  private String converterVersion;
  private String updateTime;
  private String apiVersion;
  private String discoveryRevision;
  private List<InlineSchema> inlineSchemas;

  public ConversionConfiguration() {
    this.inlineSchemas = new ArrayList<InlineSchema>();
    this.inlineFields = new HashMap<String, InlineFieldDefinition>();
    this.converterVersion = "";
    this.updateTime = "";
    this.apiVersion = "";
    this.discoveryRevision = "";

    this.errors = new ArrayList<String>();
  }

  static public ConversionConfiguration fromJSON(String jsonContents) {
    Gson gson = new Gson();
    ConversionConfiguration config =  gson.fromJson(jsonContents, ConversionConfiguration.class);
    config.populateInlineFields();
    return config;
  }

  public String toJSON() {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    this.populateInlineSchemas();
    Collections.sort(this.inlineSchemas);
    return gson.toJson(this);
  }

  public void setConfigMetadata(String converterVersion, String apiVersion, String discoveryRevision, String updateTime) {
    if (this.apiVersion.length() > 0 && !this.apiVersion.equals(apiVersion)) {
      throw new IllegalStateException(
          String.format("trying to override apiVersion %s with %s", this.apiVersion, apiVersion));
    }
    if (this.discoveryRevision.length() > 0 && this.discoveryRevision.compareTo(discoveryRevision) > 0) {
      throw new IllegalStateException(
          String.format("trying to override discoveryRevision %s with %s", this.discoveryRevision, discoveryRevision));
    }
    this.converterVersion = converterVersion;
    this.apiVersion = apiVersion;
    this.discoveryRevision = discoveryRevision;
    this.updateTime = updateTime;
  }

  public String getMessageNameForPath(String schemaPath) {
    InlineFieldDefinition inlineSchema = this.inlineFields.get(schemaPath);
    if (inlineSchema == null) {
      return null;
    }
    return inlineSchema.findProtoTypeNameForPath(schemaPath);
  }

  /**
   * Registers in this.inlineFields a single instance of schema being used as the protoTypeName
   * type of the field at fieldPath.
   */
  public InlineFieldDefinition addInlineField(String fieldPath, String protoTypeName, String schema, boolean readingFromFile) {
    InlineFieldDefinition fieldInstance = this.inlineFields.get(fieldPath);
    if (fieldInstance == null) {
      fieldInstance = new InlineFieldDefinition(protoTypeName, schema);
      this.inlineFields.put(fieldPath, fieldInstance);
    } else if (readingFromFile) {
      this.errors.add(String.format("- field specified multiple times: %s", fieldPath));
    }

    fieldInstance.update(fieldPath, protoTypeName, schema, readingFromFile);
    this.errors.addAll(fieldInstance.errors);
    return fieldInstance;
  }

  public InlineFieldDefinition addInlineField(String fieldPath, String protoTypeName, String schema) {
    return addInlineField(fieldPath, protoTypeName, schema, false);
  }

  public boolean publicFieldsEqual(ConversionConfiguration other, boolean matchDiscoveryRevision) {
    if (!(this.converterVersion.equals(other.converterVersion) &&
        this.apiVersion.equals(other.apiVersion) &&
        (!matchDiscoveryRevision || (this.discoveryRevision.equals(other.discoveryRevision))) &&
        this.inlineSchemas.size() == other.inlineSchemas.size() &&
        this.inlineFields.size() == other.inlineFields.size())) {
      return false;
    }

    for (Map.Entry<String, InlineFieldDefinition> thisEntry : this.inlineFields.entrySet()) {
      String fieldPath = thisEntry.getKey();
      InlineFieldDefinition thisInlineField = thisEntry.getValue();
      InlineFieldDefinition otherInlineField = other.inlineFields.get(fieldPath);
      if (otherInlineField == null || !thisInlineField.equals(otherInlineField)){
        return false;
      }
    }
    return true;
  }

  /**
   * Inner class to capture the occurrences of exactly one inline schema, possibly identically
   * defined in multiple places and possibly assigned different proto3 message type names for each
   * of those places. These data are reflected in the external config.
   */
  static class InlineSchema implements Comparable<InlineSchema>{
    // A human-readable representation of the schema. This is not inspected by the code in anyway
    // except to check for equality.
    private String schema;

    // Map of proto3 message type names that implement this schema to the locations of fields with
    // this type.
    private Map<String, List<String>> locations;

    transient String sortKey;

    private InlineSchema() {
      this.locations = new HashMap<String, List<String>>();
      this.sortKey = null;
    }

    /**
     * Adds fieldSchemaInstance to this, meaning that it registers fieldSchemaInstance.locations as
     * field paths (in this.locations) associated with this schema under the proto3 message name
     * inlineField.protoMessageName.  This checks that this.schema equals inlineField.schema, or, if
     * the former is null, sets it to the latter.
     *
     * Returns a list of errors.
     */
    private List<String> addFieldInstance(InlineFieldDefinition inlineField) {
      String protoTypeName = inlineField.protoMessageName;
      String schema = inlineField.schema;
      List<String> errors = new ArrayList<String>();

      assert schema != null;
      assert this.schema == null || this.schema.equals(schema);

      this.schema = schema;
      List<String> currentLocations = this.locations.get(protoTypeName);
      if (currentLocations == null) {
        currentLocations = new ArrayList<String>();
        this.locations.put(protoTypeName, currentLocations);
      }

      String newLocation = inlineField.location;
      if (currentLocations.contains(newLocation)) {
        errors.add(String.format("!! location was already registered: %s", newLocation));
      }
      currentLocations.add(newLocation);
      return errors;
    }

    public String getSortKey() {
      if (this.sortKey == null) {
        int numLocations = 0;
        String firstMessageName = null;
        for (Map.Entry<String,List<String>> oneEntry : this.locations.entrySet()) {
          String messageName = oneEntry.getKey();
          numLocations += oneEntry.getValue().size();
          if (firstMessageName == null ||
              messageName.compareTo(firstMessageName) < 0) {
            firstMessageName = messageName;
          }
        }

        // Sort schemas with most instances first. Break ties by sorting schemas with the lowest
        // proto message name first.
        this.sortKey = String.format("%03d.%s", 999-numLocations, firstMessageName);
      }
      return this.sortKey;
    }

    @Override
    public int compareTo(InlineSchema other) {
      return this.getSortKey().compareTo(other.getSortKey());
    }
  }


  // Map of distinct field paths to a InlineFieldDefinition. Fields that use identical schemas
  // will still point to unique InlineFieldDefinition objects. The schemas are combined in
  // populateInlineSchemas, which is called before writing out the output config.
  private transient Map<String, InlineFieldDefinition> inlineFields;

  private transient List<String> errors = new ArrayList<String>();

  /**
   * Populates this.inlineFields (checked to be initially empty) from this.inlineSchemas,
   * as would have been read in from an external config. Returns this.fieldToProtoType.
   */
  private Map<String, InlineFieldDefinition> populateInlineFields() {
    assert this.inlineFields.size() == 0;
    for (InlineSchema oneInlineSchema : this.inlineSchemas) {
      for (Map.Entry<String, List<String>> protoTypeToFields : oneInlineSchema.locations.entrySet()) {
        String protoTypeName = protoTypeToFields.getKey();
        for (String oneFieldPath : protoTypeToFields.getValue()) {
          this.addInlineField(oneFieldPath, protoTypeName, oneInlineSchema.schema, true);
        }
      }
    }
    this.throwIfError();
    return this.inlineFields;
  }

  /**
   * Clears and populates this.inlineSchemas from this.inlineFields, which would have been updated by the converter processing the current Discovery file.
   */
  private ConversionConfiguration populateInlineSchemas() {
    Map<String,InlineSchema> schemaToDetails = new HashMap<String, InlineSchema>(); // Keys are schemas
    for (Map.Entry<String, InlineFieldDefinition> oneInlineField : this.inlineFields.entrySet()) {
      String fieldPath = oneInlineField.getKey();
      InlineFieldDefinition fieldDefinition = oneInlineField.getValue();
      if (!fieldDefinition.schemaUsed) {
        errors.add(String.format("- previously specified field of type %s is no longer used: %s",
                fieldDefinition.protoMessageName, fieldPath));
        continue;
      }
      InlineSchema thisInlineSchema = schemaToDetails.get(fieldDefinition.schema);
      if (thisInlineSchema == null) {
        thisInlineSchema = new InlineSchema();
        schemaToDetails.put(fieldDefinition.schema, thisInlineSchema);
      }
      this.errors.addAll(thisInlineSchema.addFieldInstance(fieldDefinition));
    }

    this.inlineSchemas = new ArrayList<InlineSchema>(schemaToDetails.values());
    verifyInlineSchemas();

    this.throwIfError();

    return this;
  }

  // TODO: Test identical schemas with different names
  /** Consistency check that all the `InlineFieldDefinition` instances from which we populated `inlineSchemas` have consistent message names. */
  private void verifyInlineSchemas() {
    Map<String, Set<String>> protoNameToSchema = new HashMap<String,Set<String>>();
    for (InlineSchema oneInlineSchema : this.inlineSchemas) {
      String schemaDefinition = oneInlineSchema.schema;
      for (Map.Entry<String, List<String>> entry : oneInlineSchema.locations.entrySet()) {
        String messageName = entry.getKey();
        List<String> messageLocations = entry.getValue();
        for (String oneLocation : messageLocations) {
          InlineFieldDefinition fieldDefinition = this.inlineFields.get(oneLocation);
          if (fieldDefinition == null) {
            errors.add(String.format("- inconsistency: did not find InlineFieldDefinition at %s", oneLocation));
            continue;
          }
          if (!messageName.equals(fieldDefinition.protoMessageName)) {
            errors.add(String.format("- inconsistency: expected message name '%s' but got '%s' at location %s",
                    messageName, fieldDefinition.protoMessageName,
                    oneLocation));
          }
          if (!schemaDefinition.equals(fieldDefinition.schema)) {
            errors.add(String.format("- inconsistency: schema for protobuf name '%s' for field %s is '%s' rather than '%s'",
                messageName, oneLocation, fieldDefinition.schema, schemaDefinition));
          }

          Set<String> schemasForThisProtoName = protoNameToSchema.get(messageName);
          if (schemasForThisProtoName == null) {
            schemasForThisProtoName = new HashSet<String>();
            protoNameToSchema.put(messageName, schemasForThisProtoName);
          }
          schemasForThisProtoName.add(fieldDefinition.schema);
        }
      }
    }  // for this.inlineSchemas

    for (Map.Entry<String, Set<String>> protoMessageSchema : protoNameToSchema.entrySet()) {
      Set<String> schemas = protoMessageSchema.getValue();
      if (schemas.size() != 1) {
        errors.add(String.format("- invalid configuration: proto message '%s' configured for multiple schemas: %s",
                protoMessageSchema.getKey(), String.join(", ", schemas)));
      }
    }
  }

  private void throwIfError() {
    if (this.errors.size() > 0) {
      throw new IllegalStateException(String.join("\n", this.errors));
    }
  }

  /**
   *  Inner class to capture fields with inline-defined schemas. While several fields may use
   *  identical inline-defined schemas that wind up referring to the same protobuf type, this class
   *  should be instantiated once per field, not once per protobuf type. This class is used for
   *  internal computations and is not directly reflected in the external config.
   */
  static class InlineFieldDefinition {
    // Each InlineFieldDefinition should have a distinct protoMessageName.
    private String protoMessageName;

    // Multiple InlineFieldDefinition instances may share the same schema. Note that when
    // reading the external config, we always set schemaUsed to false. We only set it to true via
    // the update method that is transitively called from the DocumentToProtoConverter.
    private String schema;
    private boolean schemaUsed;

    // Any location should appear at most once in this field across all InlineFieldDefinition instances.
    private String location;

    private List<String> errors;

    public InlineFieldDefinition(String protoTypeName, String schema) {
      this.location = null;
      this.protoMessageName = protoTypeName;
      this.schema = schema;
      this.errors = new ArrayList<String>();
    }

    private String findProtoTypeNameForPath(String schemaPath) {
      if (this.location != null && this.location.equals(schemaPath)) {
        return this.protoMessageName;
      }

      this.errors.add(String.format("- requested name for path \"%s\" in object with path %s",
              schemaPath, this.location));
      return null;
    }

    // Updates this InlineFieldDefinition, setting schemaUsed if not readingFromFile, and
    // erroring if schemaUsed was already set.
    public void update(String fieldPath, String protoTypeName, String schema, boolean readingFromFile) {
      if (this.location != null && !this.location.equals(fieldPath)) {
        this.errors.add(String.format("- inconsistency: trying to update location of inline schema instance %s -> %s",
                this.location, fieldPath));
      }
      if (this.schemaUsed) {
        this.errors.add(String.format("- inconsistency: this InlineFieldDefinition was already used: %s:%s:%s\n",
                protoTypeName, fieldPath, schema));
      }
      if (!this.protoMessageName.equals(protoTypeName)) {
        this.errors.add(String.format("- invalid update: trying to rename type for field %s from '%s' to '%s'",
            fieldPath, this.protoMessageName, protoTypeName));
      }

      this.location = fieldPath;
      this.schema = schema;
      this.schemaUsed = !readingFromFile;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof InlineFieldDefinition) || obj == null) {
        return false;
      }
      InlineFieldDefinition other = (InlineFieldDefinition) obj;
      if (other == null ||
          !this.protoMessageName.equals(other.protoMessageName) ||
          (this.schema != null && !this.schema.equals(other.schema)) ||
          ((this.location == null) != (other.location == null)) ||
          ((this.location != null && !this.location.equals(other.location))) ){
        return false;
      }

      return true;
    }
  }



}

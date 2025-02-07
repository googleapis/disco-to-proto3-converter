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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

public class ConversionConfiguration {

  /**
   *  Inner class to capture fields with inline-defined schemas. While several fields may use
   *  identical inline-defined schemas that wind up referring to the same protobuf type, this class
   *  should be instantiated once per field, not once per protobuf type. This class is used for
   *  internal computations and is not directly reflected in the external config.
   */
  static class InlineFieldSchemaInstance {
    // Each InlineFieldSchemaInstance should have a distinct protoMessageName.
    private String protoMessageName;

    // Multiple InlineFieldSchemaInstance instances may share the same schema. Note that when
    // reading the external config, we always set schemaUsed to false. We only set it to true via
    // the update method that is transitively called from the DocumentToProtoConverter.
    private String schema;
    private boolean schemaUsed;

    // Any location should appear at most once in this field across all InlineFieldSchemaInstance instances.
    private List<String> locations;

    private List<String> errors;

    public InlineFieldSchemaInstance(String protoTypeName, String schema) {
      this.locations = new ArrayList<String>();
      this.protoMessageName = protoTypeName;
      this.schema = schema;
      this.errors = new ArrayList<String>();
    }

    // Updates this InlineFieldSchemaInstance, setting schemaUsed if not readingFromFile, and
    // erroring if schemaUsed was already set.
    public void update(String fieldPath, String protoTypeName, String schema, boolean readingFromFile) {
      if (!this.locations.contains(fieldPath)) {
        this.locations.add(fieldPath);
      }
      if (this.schemaUsed) {
        this.errors.add(String.format("!! this InlineFieldSchemaInstance was already used: %s:%s:%s\n",
                protoTypeName, fieldPath, schema));
      }

      this.protoMessageName = protoTypeName;
      this.schema = schema;
      this.schemaUsed = !readingFromFile;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof InlineFieldSchemaInstance) || obj == null) {
        return false;
      }
      InlineFieldSchemaInstance other = (InlineFieldSchemaInstance)obj;
      if (other == null ||
          !this.protoMessageName.equals(other.protoMessageName) ||
          // Separate the null here, and when we use the schema (not read from external config) set
          // a new internal field that says used. Also change places where we check for null
          // schema. This will initially cause the readWriteWIthoutAnyChanges test to fail.
          (this.schema != null && !this.schema.equals(other.schema))||
          this.locations.size() != other.locations.size()) {
        return false;
      }
      for (String oneLocation : this.locations) {
        if (!other.locations.contains(oneLocation)) {
          return false;
        }
      }
      return true;
    }
  }

  /**
   * Inner class to capture the occurrences of exactly one inline schema, possibly identically
   * defined in multiple places and possibly assigned different proto3 message type names for each
   * of those places. These data are reflected in the external config.
   */
  static class InlineSchema {
    // A human-readable representation of the schema. This is not inspected by the code in anyway
    // except to check for equality.
    private String schema;

    // Map of proto3 message type names that implement this schema to the locations of fields with
    // this type.
    private Map<String, List<String>> locations;

    private InlineSchema() {
      this.locations = new HashMap<String, List<String>>();
    }

    /**
     * Adds fieldSchemaInstance to this, meaning that it registers fieldSchemaInstance.locations as field paths
     * (in this.locations) associated with this schema under the proto3 message name
     * fieldSchemaInstance.protoMessageName.  This checks that this.schema equals fieldSchemaInstance.schema, or,
     * if the former is null, sets it to the latter.
     *
     * Returns a list of errors.
     */
    public List<String> addFieldInstance(InlineFieldSchemaInstance fieldSchemaInstance) {
      String protoTypeName = fieldSchemaInstance.protoMessageName;
      String schema = fieldSchemaInstance.schema;
      List<String> errors = new ArrayList<String>();


      assert schema != null;
      assert this.schema == null || this.schema == schema;

      this.schema = schema;
      List<String> currentLocations = this.locations.get(protoTypeName);
      if (currentLocations == null) {
        currentLocations = new ArrayList<String>();
        this.locations.put(protoTypeName, currentLocations);
      }
      for (String newLocation : fieldSchemaInstance.locations) {
        if (currentLocations.contains(newLocation)) {
          errors.add(String.format("!! location was already registered: %s", newLocation));
          continue;
        }
        currentLocations.add(newLocation);
      };
      return errors;
    }
  }

  /* BEGIN: Only these fields are exposed in the external proto config. */
  // - https://stackoverflow.com/a/50934756
  // - https://www.baeldung.com/spring-git-information
  private final String converterVersion;

  private final String apiVersion;
  private final String discoveryRevision;

  private List<InlineSchema> inlineSchemas;
  /* END: Only these fields are exposed in the external proto config. */


  // Map of distinct field paths to a InlineFieldSchemaInstance. Fields that use identical schemas
  // will still point to unique InlineFieldInstance objects. The schemas are combined in
  // PopulateInlineSchemas, which is called before writing out the output config.
  private transient Map<String, InlineFieldSchemaInstance> fieldToSchemaInstance;

  private transient List<String> errors = new ArrayList<String>();


  public ConversionConfiguration() {
    this.inlineSchemas = new ArrayList<InlineSchema>();
    this.fieldToSchemaInstance = new HashMap<String, InlineFieldSchemaInstance>();
    this.converterVersion = "UNSET";
    this.apiVersion = "UNSET";
    this.discoveryRevision = "UNSET";

    this.errors = new ArrayList<String>();
  }


  public InlineFieldSchemaInstance addInlineSchemaInstance(String fieldPath, String protoTypeName, String schema) {
    return addInlineSchemaInstance(fieldPath, protoTypeName, schema, false);
  }

  public void throwIfError() {
    if (this.errors.size() > 0) {
      throw new IllegalStateException(String.join("\n", this.errors));
    }
  }

  /**
   * Registers in this.fieldToProtoType a single instance of schema being used as the protoTypeName
   * type of the field at fieldPath.
   */
  public InlineFieldSchemaInstance addInlineSchemaInstance(String fieldPath, String protoTypeName, String schema, boolean readingFromFile) {
    InlineFieldSchemaInstance fieldInstance = this.fieldToSchemaInstance.get(fieldPath);
    if (fieldInstance == null) {
      fieldInstance = new InlineFieldSchemaInstance(protoTypeName, schema);
      this.fieldToSchemaInstance.put(fieldPath, fieldInstance);
    } else if (readingFromFile) {
      this.errors.add(String.format("!! field specified multiple times: %s", fieldPath));
    }
    fieldInstance.update(fieldPath, protoTypeName, schema, readingFromFile);
    errors.addAll(fieldInstance.errors);
    return fieldInstance;
  }


  /**
   * Populates this.fieldToSchemaInstance (checked to be initially empty) from this.inlineSchemas,
   * as would have been read in from an external config. Returns this.fieldToProtoType.
   */
  public Map<String, InlineFieldSchemaInstance> PopulateFieldToSchemaInstance() {
    assert this.fieldToSchemaInstance.size() == 0;
    for (InlineSchema oneInlineSchema : this.inlineSchemas) {
      for (Map.Entry<String, List<String>> protoTypeToFields : oneInlineSchema.locations.entrySet()) {
        String protoTypeName = protoTypeToFields.getKey();
        for (String oneFieldPath : protoTypeToFields.getValue()) {
          this.addInlineSchemaInstance(oneFieldPath, protoTypeName, oneInlineSchema.schema, true);
        }
      }
    }
    this.throwIfError();
    return this.fieldToSchemaInstance;
  }

  /**
   * Clears and populates this.inlineSchemas from this.fieldToSchemaInstance, which would have been updated by the converter processing the current Discovery file.
   */
  public ConversionConfiguration PopulateInlineSchemas() {
    Map<String,InlineSchema> schemaToDetails = new HashMap<String, InlineSchema>(); // Keys are schemas
    for (Map.Entry<String, InlineFieldSchemaInstance> fieldToSchemaInstance : this.fieldToSchemaInstance.entrySet()) {
      String fieldPath = fieldToSchemaInstance.getKey();
      InlineFieldSchemaInstance InlineFieldSchemaInstance = fieldToSchemaInstance.getValue();
      if (!InlineFieldSchemaInstance.schemaUsed) {
        errors.add(String.format("!! previously specified field of type %s is not longer used: %s",
                InlineFieldSchemaInstance.protoMessageName, fieldPath));
        continue;
      }
      InlineSchema thisInlineSchema = schemaToDetails.get(InlineFieldSchemaInstance.schema);
      if (thisInlineSchema == null) {
        thisInlineSchema = new InlineSchema();
        schemaToDetails.put(InlineFieldSchemaInstance.schema, thisInlineSchema);
      }
      this.errors.addAll(thisInlineSchema.addFieldInstance(InlineFieldSchemaInstance));
    }
    this.throwIfError();
    this.inlineSchemas = new ArrayList<InlineSchema>(schemaToDetails.values());
    // TODO: Consider sorting the schemas by number of instances
    return this;
  }

  static public ConversionConfiguration FromJSON(String jsonContents) {
    Gson gson = new Gson();
    ConversionConfiguration config =  gson.fromJson(jsonContents, ConversionConfiguration.class);
    config.PopulateFieldToSchemaInstance();
    return config;
  }

  public String ToJSON() {
    Gson gson = new Gson();
    this.PopulateInlineSchemas();
    return gson.toJson(this);
  }

  public boolean publicFieldsEqual(ConversionConfiguration other, boolean matchDiscoveryRevision) {
    if (!(this.converterVersion.equals(other.converterVersion) &&
        this.apiVersion.equals(other.apiVersion) &&
        (!matchDiscoveryRevision || (this.discoveryRevision.equals(other.discoveryRevision))) &&
        this.inlineSchemas.size() == other.inlineSchemas.size() &&
        this.fieldToSchemaInstance.size() == other.fieldToSchemaInstance.size())) {
      return false;
    }

    for (Map.Entry<String, InlineFieldSchemaInstance> thisEntry : this.fieldToSchemaInstance.entrySet()) {
      String fieldPath = thisEntry.getKey();
      InlineFieldSchemaInstance thisSchemaInstance = thisEntry.getValue();
      InlineFieldSchemaInstance otherSchemaInstance = other.fieldToSchemaInstance.get(fieldPath);
      if (otherSchemaInstance == null || !thisSchemaInstance.equals(otherSchemaInstance)) {
        return false;
      }
    }
    return true;
  }
}

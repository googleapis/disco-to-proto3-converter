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
   *  Inner class to capture the uses of a distinct proto message type. This class is used for
   *  internal computations and is not directly reflected in the external config.
   */
  static class DistinctProtoType {
    // Each DistinctProtoType instance should have a distinct protoMessageName.
    private String protoMessageName;

    // Multiple DistinctProtoType instances may share the same schema. Note that when reading the
    // external config, we always set the schema to null. We only set the schemas to non-null
    // through the addLocation method that is transitively called from the DocumentToProtoConverter,
    // and only those schemas that are set (non-null) get emitted back out.
    private String schema;

    // Any location should appear at most once in this field across all DistinctProtoType instances.
    private List<String> locations;

    private boolean errors;

    public DistinctProtoType(String protoTypeName, String schema) {
      this.locations = new ArrayList<String>();
      this.protoMessageName = protoTypeName;
      this.schema = schema;
    }

    // Overrides this.protoMessageName (we used to check they matched)
    public void addLocation(String fieldPath, String protoTypeName, String schema) {
      if (!this.locations.contains(fieldPath)) {
        this.locations.add(fieldPath);
      }
      if (this.schema != null && schema != this.schema) {
        errors = true;
      }
      this.protoMessageName = protoTypeName;
      this.schema = schema;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      }
      if (!(obj instanceof DistinctProtoType) || obj == null) {
        return false;
      }
      DistinctProtoType other = (DistinctProtoType)obj;
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
     * Adds theProtoType to this, meaning that it registers thePrototype.locations as field paths
     * (in this.locations) associated with this schema under the proto3 message name
     * theProtoType.protoMessageName.  This checks that this.schema equals theProtoType.schema, or,
     * if the former is null, sets it to the latter.
     *
     * DELETE: This also checks that no locations were previously provided for
     * thePrototype.protoMessageName.
     */
    public DistinctProtoType addProtoType(DistinctProtoType theProtoType) {
      String protoTypeName = theProtoType.protoMessageName;
      String protoSchema = theProtoType.schema;

      assert protoSchema != null;
      assert this.schema == null || this.schema == protoSchema;
      //      assert this.locations.get(protoTypeName) == null;

      this.schema = protoSchema;
      List<String> currentLocations = this.locations.get(protoTypeName);
      if (currentLocations == null) {
        currentLocations = new ArrayList<String>();
        this.locations.put(protoTypeName, currentLocations);
      }
      for (String newLocation : theProtoType.locations) {
        if (currentLocations.contains(newLocation)) {
          throw new IllegalStateException(String.format("location was already registered: %s", newLocation));
        }
        currentLocations.add(newLocation);
      };

      return theProtoType;
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

  public ConversionConfiguration() {
    this.inlineSchemas = new ArrayList<InlineSchema>();
    this.fieldToProtoType = new HashMap<String, DistinctProtoType>();
    this.converterVersion = "UNSET";
    this.apiVersion = "UNSET";
    this.discoveryRevision = "UNSET";
  }

  // Map of distinct field paths to a DistinctProtoType. Keys that describe fields with the same
  // proto3 message type should point to the same DistinctProtoType instance, and that instance
  // should include each of those field locations.
  private transient Map<String, DistinctProtoType> fieldToProtoType;

  /**
   * Registers in this.fieldToProtoType a single instance of schema being used as the protoTypeName
   * type of the field at fieldPath.
   */
  public DistinctProtoType addInlineSchemaInstance(String fieldPath, String protoTypeName, String schema, boolean requireNew /* remove?*/) {
    DistinctProtoType protoType = this.fieldToProtoType.get(fieldPath);
    if (protoType == null) {
      protoType = new DistinctProtoType(protoTypeName, schema);
      this.fieldToProtoType.put(fieldPath, protoType);
    } else if (requireNew) {
      throw new IllegalStateException(String.format("field specified multiple times: %s", fieldPath));
    }
    protoType.addLocation(fieldPath, protoTypeName, schema);
    return protoType;
  }


  /**
   * Populates this.fieldToProtoType (checked to be initially empty) from this.inlineSchemas, as
   * would have been read in from an external config. Returns this.fieldToProtoType.
   */
  public Map<String, DistinctProtoType> PopulateFieldToProtoType() {
    assert this.fieldToProtoType.size() == 0;
    for (InlineSchema oneInlineSchema : this.inlineSchemas) {
      for (Map.Entry<String, List<String>> protoTypeToFields : oneInlineSchema.locations.entrySet()) {
        String protoTypeName = protoTypeToFields.getKey();
        for (String oneFieldPath : protoTypeToFields.getValue()) {
          DistinctProtoType protoType = this.addInlineSchemaInstance(oneFieldPath, protoTypeName, null,true);
          assert !protoType.errors;
        }
      }
    }
    return this.fieldToProtoType;
  }

  /**
   * Clears and populates this.inlineSchemas from this.fieldToProtoType
   */
  public ConversionConfiguration PopulateInlineSchemas() {
    Map<String,InlineSchema> schemaToDetails = new HashMap<String, InlineSchema>(); // Keys are schemas
    for (Map.Entry<String, DistinctProtoType> fieldToProto : this.fieldToProtoType.entrySet()) {
      String fieldPath = fieldToProto.getKey();
      DistinctProtoType thisDistinctProtoType = fieldToProto.getValue();
      if (thisDistinctProtoType.schema == null) {
        // If thisDistinctProtoType was read in, it was set to null, and if the correspond proto
        // type was renamed in the config, the read-in DistinctProtoType would still have schema
        // being null. That would be fine, except that the fieldPath must point to a type that is non-null. So if we're here, that's an error.
        throw new IllegalStateException(String.format("previously specified field of type %s is not longer used: %s",
                thisDistinctProtoType.protoMessageName, fieldPath));
      }
      InlineSchema thisInlineSchema = schemaToDetails.get(thisDistinctProtoType.schema);
      if (thisInlineSchema == null) {
        thisInlineSchema = new InlineSchema();
        schemaToDetails.put(thisDistinctProtoType.schema, thisInlineSchema);
      }
      thisInlineSchema.addProtoType(thisDistinctProtoType);
    }
    this.inlineSchemas = new ArrayList<InlineSchema>(schemaToDetails.values());
    return this;
  }

  static public ConversionConfiguration FromJSON(String jsonContents) {
    Gson gson = new Gson();
    ConversionConfiguration config =  gson.fromJson(jsonContents, ConversionConfiguration.class);
    config.PopulateFieldToProtoType();
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
        this.fieldToProtoType.size() == other.fieldToProtoType.size())) {
      return false;
    }

    for (Map.Entry<String, DistinctProtoType> thisEntry : this.fieldToProtoType.entrySet()) {
      String fieldPath = thisEntry.getKey();
      DistinctProtoType thisProtoType = thisEntry.getValue();
      DistinctProtoType otherProtoType = other.fieldToProtoType.get(fieldPath);
      if (otherProtoType == null || !thisProtoType.equals(otherProtoType)) {
        return false;
      }
    }
    return true;
  }
}

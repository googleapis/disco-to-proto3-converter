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

    // Multiple DistinctProtoType instances may share the same schema.
    private String schema;

    // Any location should appear at most once in this field across all DistinctProtoType instances.
    private List<String> locations;

    private boolean errors;

    public DistinctProtoType(String protoTypeName, String schema) {
      this.locations = new ArrayList<String>();
      this.protoMessageName = protoTypeName;
      this.schema = schema;
    }

    public void addLocation(String fieldPath, String protoTypeName, String schema) {
      this.locations.add(fieldPath);
      if (protoTypeName != this.protoMessageName || (this.schema != null && schema != this.schema)) {
        errors = true;
      }
      this.schema = schema;
    }
  }

  /**
   * Inner class to capture the occurrences of exactly one inline schema, possibly identically
   * defined in multiple places and possibly assigned different proto3 message type names for each
   * of those places. These data are reflected in the external config.
   */
  static class InlineSchema {
    // A human-readable representation of the schema. This is not inspected by the code in anyway except to check for equality.
    private final String schema;

    // Map of proto3 message type names that implement this schema to the locations of fields with this type.
    private Map<String, List<String>> locations;

    /**
     * Adds theProtoType to this, meaning that it registers thePrototype.locations as field paths
     * (in this.locations) associated with this schema under the proto3 message name
     * theProtoType.protoMessageName.  This checks that this.schema equals theProtoType.schema, or,
     * if the former is null, sets it to the latter. This also checks that no locations were
     * previously provided for thePrototype.protoMessageName.
     */
    public DistinctProtoType addProtoType(DistinctProtoType theProtoType) {
      String protoTypeName = theProtoType.protoMessageName;
      String protoSchema = theProtoType.schema;

      assert protoSchema != null;
      assert this.schema == null || this.schema == protoSchema;
      assert this.locations.get(protoTypeName) == null;

      this.schema = protoSchema;
      this.locations.put(protoTypeName, thePrototype.locations);

      return theProtoType;
    }
  }

  /* BEGIN: Only these fields are exposed in the external proto config. */
  // - https://stackoverflow.com/a/50934756
  // - https://www.baeldung.com/spring-git-information
  private final String converterVersion;

  private final String apiVersion;
  private String discoveryRevision;

  private List<InlineSchema> inlineSchemas;
  /* END: Only these fields are exposed in the external proto config. */

  public ConversionConfiguration() {
    this.inlineSchemas = new ArrayList<String>();
    this.fieldToProtoType = new HashMap<String, DistinctProtoType>();
  }

  // Map of distinct field paths to a DistinctProtoType. Keys that describe fields with the same
  // proto3 message type should point to the same DistinctProtoType instance, and that instance
  // should include each of those field locations.
  private transient Map<String, DistinctProtoType> fieldToProtoType;

  /**
   * Registers in this.fieldToProtoType a single instance of schema being used as the protoTypeName
   * type of the field at fieldPath.
   */
  private DistinctProtoType addInlineSchemaInstance(String fieldPath, String protoTypeName, String schema, boolean requireNew /* remove?*/) {
    DistinctProtoType protoType = this.fieldToProtoType.get(fieldPath);
    if (protoType == null) {
      protoType = new DistinctProtoType(protoTypeName, schema);
      this.fieldToProtoType.put(fieldPath, protoType);
    } else if (requireNew) {
      throw IllegalStateException(String.format("field specified multiple times: %s", fieldPath));
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
    this.inlineSchemas.clear();
    Map<String,InlineSchema> schemaToDetails = new HashMap<String, InlineSchema>(); // Keys are schemas
    for (Map.Entry<String, DistinctProtoType> fieldToProto : this.fieldToProtoType) {
      String fieldPath = fieldToProto.getKey();
      DistinctProtoType thisDistinctProtoType = fieldToProto.getValue();
      InlineSchema thisInlineSchema = schemaToDetails.get(thisDistinctProtoType.schema);
      if (thisInlineSchema == null) {
        thisInlineSchema = new InlineSchema();
        schemaToDetails.put(thisInlineSchema);
      }
      thisInlineSchema.addProtoType(thisDistinctProtoType);
    }
    this.inlineSchemas = schemaToDetails.values();
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

}

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
package com.google.cloud.discotoproto3converter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import com.google.cloud.discotoproto3converter.proto3.ConversionConfiguration;
import com.google.gson.Gson;
import org.junit.Test;

public class ConversionConfigurationTest {
  String inputConfig =
      """
        {
       "converterVersion": "some-identifier",
       "apiVersion": "gamma",
       "discoveryRevision": "20250204",
       "inlineSchemas": [
          {
            "schema": "an initial schema",
            "locations": {
              "LakeInfo": ["schemas.Lake.info", "schemas.BigLake.lakeInfo"],
              "PondInfo": ["schemas.Pond.info", "schemas.BigPond.pondInfo"]
            }
           }
       ]
       }
""";

  @Test
  public void publicFieldsEqual() {
    System.out.printf("** publicFieldsEqual\n");
    Gson gson = new Gson();
    String referenceConfig =
        """
            {
           "converterVersion": "A",
           "apiVersion": "B",
           "discoveryRevision": "C",
           "inlineSchemas": [
              {
                "schema": "D",
                "locations": {
                  "E": ["F", "G"],
                  "H": ["I", "J"]
                }
              },
              {
                "schema": "K",
                "locations": {
                  "L": ["M", "N"],
                  "O": ["P", "Q"]
                }
               }
           ]
           }
        """;
    assert ConversionConfiguration.checkIdenticalJSON(referenceConfig, referenceConfig);

    // Check that changing the order of the lists or maps doesn't affect equality.
    String variantConfig =
        """
            {
           "converterVersion": "A",
           "apiVersion": "B",
           "discoveryRevision": "C",
           "inlineSchemas": [
              {
                "schema": "K",
                "locations": {
                  "L": ["M", "N"],
                  "O": ["Q", "P"]
                }
              },
              {
                "schema": "D",
                "locations": {
               "H": ["I", "J"],
                  "E": ["G", "F"]
                }
              }
           ]
           }
        """;
    assert ConversionConfiguration.checkIdenticalJSON(referenceConfig, variantConfig);

    // Changing the schema makes the configs not equal
    variantConfig =
        """
            {
           "converterVersion": "A",
           "apiVersion": "B",
           "discoveryRevision": "C",
           "inlineSchemas": [
              {
                "schema": "NOT-D",
                "locations": {
                  "E": ["F", "G"],
                  "H": ["I", "J"]
                }
              },
              {
                "schema": "K",
                "locations": {
                  "L": ["M", "N"],
                  "O": ["P", "Q"]
                }
               }
           ]
           }
        """;
    assertFalse(ConversionConfiguration.checkIdenticalJSON(referenceConfig, variantConfig));

    // Changing the locations makes the configs not equal
    variantConfig =
        """
            {
           "converterVersion": "A",
           "apiVersion": "B",
           "discoveryRevision": "C",
           "inlineSchemas": [
              {
                "schema": "D",
                "locations": {
                  "E": ["NOT-F", "G"],
                  "H": ["I", "J"]
                }
              },
              {
                "schema": "K",
                "locations": {
                  "L": ["M", "N"],
                  "O": ["P", "Q"]
                }
               }
           ]
           }
        """;
    System.out.printf("    locations check\n");
    assertFalse(ConversionConfiguration.checkIdenticalJSON(referenceConfig, variantConfig));
  }

  @Test
  public void getMessageNameForPath() {
    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);

    assert "LakeInfo".equals(config.getMessageNameForPath("schemas.Lake.info"));
    assert "LakeInfo".equals(config.getMessageNameForPath(new String("schemas.BigLake.lakeInfo")));
    assert "PondInfo".equals(config.getMessageNameForPath(new String("schemas.Pond.info")));
    assert "PondInfo".equals(config.getMessageNameForPath("schemas.BigPond.pondInfo"));

    assertNull(config.getMessageNameForPath("schemas.Mountain.info"));
  }

  @Test
  public void readWriteWithoutAnyChanges() {
    String label = "readWriteWithoutAnyChanges";

    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);

    config.addInlineField("schemas.Pond.info", "PondInfo", "an initial schema");
    config.addInlineField("schemas.BigLake.lakeInfo", "LakeInfo", "an initial schema");
    config.addInlineField("schemas.Lake.info", "LakeInfo", "an initial schema");
    config.addInlineField("schemas.BigPond.pondInfo", "PondInfo", "an initial schema");
    String outputConfig = config.toJSON();

    System.out.printf(
        "** %s: input\n%s\n%s: output\n%s\n", label, inputConfig, label, outputConfig);
    assert ConversionConfiguration.checkIdenticalJSON(inputConfig, outputConfig);
  }

  @Test
  public void readWriteUsingSchemaTwice() {
    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);
    config.addInlineField("schemas.Pond.info", "PondInfo", "an initial schema");
    config.addInlineField("schemas.BigLake.lakeInfo", "LakeInfo", "an initial schema");
    config.addInlineField("schemas.Lake.info", "LakeInfo", "an initial schema");
    config.addInlineField(
        "schemas.Lake.info", "LakeInfo", "an initial schema"); // should cause error
    config.addInlineField("schemas.BigPond.pondInfo", "PondInfo", "an initial schema");

    assertThrows(IllegalStateException.class, () -> config.toJSON());
  }

  @Test
  public void readWriteNotUsingOneSchema() {
    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);
    config.addInlineField("schemas.Pond.info", "PondInfo", "an initial schema");
    config.addInlineField("schemas.Lake.info", "LakeInfo", "an initial schema");
    config.addInlineField("schemas.BigPond.pondInfo", "PondInfo", "an initial schema");
    // not using schemas.BigLake.lakeInfo should cause an error

    assertThrows(IllegalStateException.class, () -> config.toJSON());
  }

  @Test
  public void readWriteRenamingProtos() {
    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);
    config.addInlineField("schemas.Pond.info", "PondInfo", "an initial schema");
    config.addInlineField("schemas.BigLake.lakeInfo", "LakeInfo", "an initial schema");
    config.addInlineField("schemas.Lake.info", "LakeInfo", "an initial schema");
    // Changing the name of a proto field type from what was previously used (as exposed in the
    // input config) is a breaking change and causes an error:
    config.addInlineField("schemas.BigPond.pondInfo", "SecondPondInfo", "an initial schema");

    assertThrows(IllegalStateException.class, () -> config.toJSON());
  }

  @Test
  public void splitSchemasBackwardsCompatible() {
    String label = "splitSchemasBackwardsCompatible";

    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);
    // we update all entries with  the same protobuf message name to refer to the same updated
    // schema. This is valid.
    config.addInlineField("schemas.Lake.info", "LakeInfo", "updated-schema");
    config.addInlineField("schemas.BigLake.lakeInfo", "LakeInfo", "updated-schema");
    config.addInlineField("schemas.Pond.info", "PondInfo", "an initial schema");
    config.addInlineField("schemas.BigPond.pondInfo", "PondInfo", "an initial schema");

    String outputConfig = config.toJSON();

    String expectedConfig =
        """
        {
       "converterVersion": "some-identifier",
       "apiVersion": "gamma",
       "discoveryRevision": "20250204",
       "inlineSchemas": [
          {
            "schema": "an initial schema",
            "locations": {
              "PondInfo": ["schemas.Pond.info", "schemas.BigPond.pondInfo"]
            }
          },
          {
            "schema": "updated-schema",
            "locations": {
              "LakeInfo": ["schemas.Lake.info", "schemas.BigLake.lakeInfo"]
            }
          }
       ]
       }
""";

    System.out.printf(
        "** %s: input\n%s\n%s: output\n%s\n%s: expected\n%s\n",
        label, inputConfig, label, outputConfig, label, expectedConfig);
    assert ConversionConfiguration.checkIdenticalJSON(expectedConfig, outputConfig);
  }

  @Test
  public void splitSchemasNonBackwardsCompatible() {

    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);
    // We update some but not all entries with the same protobuf message name LakeInfo to refer to
    // an updated schema. This is invalid because we don't update all entries with the same protobuf
    // message name.
    config.addInlineField("schemas.Lake.info", "LakeInfo", "updated-schema");
    config.addInlineField("schemas.BigLake.lakeInfo", "LakeInfo", "an initial schema");
    config.addInlineField("schemas.Pond.info", "PondInfo", "an initial schema");
    config.addInlineField("schemas.BigPond.pondInfo", "PondInfo", "an initial schema");

    assertThrows(IllegalStateException.class, () -> config.toJSON());
  }

  @Test
  public void readWriteAddingSchema() {
    String label = "readWriteAddingSchema";

    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);
    config.addInlineField("schemas.Pond.info", "PondInfo", "current schema");
    config.addInlineField("schemas.BigLake.lakeInfo", "LakeInfo", "current schema");
    config.addInlineField("schemas.Lake.info", "LakeInfo", "current schema");
    config.addInlineField("schemas.BigPond.pondInfo", "PondInfo", "current schema");
    config.addInlineField("schemas.River.info", "RiverInfo", "new schema");
    String outputConfig = config.toJSON();

    String expectedConfig =
        """
        {
       "converterVersion": "some-identifier",
       "apiVersion": "gamma",
       "discoveryRevision": "20250204",
       "inlineSchemas": [
          {
            "schema": "current schema",
            "locations": {
              "LakeInfo": ["schemas.Lake.info", "schemas.BigLake.lakeInfo"],
              "PondInfo": ["schemas.Pond.info", "schemas.BigPond.pondInfo"]
                  }
          },
          {
            "schema": "new schema",
            "locations": {
              "RiverInfo": ["schemas.River.info"]
            }
           }
       ]
       }
""";

    System.out.printf(
        "** %s: input\n%s\n%s: output\n%s\n%s: expected\n%s\n",
        label, inputConfig, label, outputConfig, label, expectedConfig);
    assert ConversionConfiguration.checkIdenticalJSON(expectedConfig, outputConfig);
  }

  @Test
  public void readWriteAddingSchemaStringVariable() {
    String label = "readWriteAddingSchema";

    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);
    // This first call uses a non-literal to ensure we are doing the correct comparisons under the
    // hood.
    config.addInlineField("schemas.Pond.info", "PondInfo", new String("current schema"));
    config.addInlineField("schemas.BigLake.lakeInfo", "LakeInfo", "current schema");
    config.addInlineField("schemas.Lake.info", "LakeInfo", "current schema");
    config.addInlineField("schemas.BigPond.pondInfo", "PondInfo", "current schema");
    config.addInlineField("schemas.River.info", "RiverInfo", "new schema");
    String outputConfig = config.toJSON();

    String expectedConfig =
        """
        {
       "converterVersion": "some-identifier",
       "apiVersion": "gamma",
       "discoveryRevision": "20250204",
       "inlineSchemas": [
          {
            "schema": "current schema",
            "locations": {
              "LakeInfo": ["schemas.Lake.info", "schemas.BigLake.lakeInfo"],
              "PondInfo": ["schemas.Pond.info", "schemas.BigPond.pondInfo"]
              }
          },
          {
            "schema": "new schema",
            "locations": {
              "RiverInfo": ["schemas.River.info"]
            }
           }
       ]
       }
""";

    System.out.printf(
        "** %s: input\n%s\n%s: output\n%s\n%s: expected\n%s\n",
        label, inputConfig, label, outputConfig, label, expectedConfig);
    assert ConversionConfiguration.checkIdenticalJSON(expectedConfig, outputConfig);
  }

  @Test
  public void readWriteWithoutUsingFieldSchema() {
    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);
    config.addInlineField("schemas.Pond.info", "PondInfo", "current schema");
    config.addInlineField("schemas.BigLake.lakeInfo", "LakeInfo", "current schema");
    config.addInlineField("schemas.Lake.info", "LakeInfo", "current schema");
    // We omit BigPond

    assertThrows(IllegalStateException.class, () -> config.toJSON());
  }

  @Test
  public void setConfigMetadata() {
    ConversionConfiguration config = ConversionConfiguration.fromJSON(inputConfig);

    // Same metadata as inputConfig
    config.setConfigMetadata("some-identifier", "gamma", "20250204", "right-now");

    // different converter version and time are fine
    config.setConfigMetadata("something else", "gamma", "20250204", "later");

    assertThrows(
        IllegalStateException.class,
        () ->
            config.setConfigMetadata(
                "some-identifier",
                "delta", // different API version causes exception
                "20250204",
                "right-now"));

    assertThrows(
        IllegalStateException.class,
        () ->
            config.setConfigMetadata(
                "some-identifier",
                "gamma",
                "20250203", // earlier revision causes error
                "right-now"));

    config.setConfigMetadata(
        "some-identifier",
        "gamma",
        "20250206", // later revision is OK
        "right-now");
  }
}

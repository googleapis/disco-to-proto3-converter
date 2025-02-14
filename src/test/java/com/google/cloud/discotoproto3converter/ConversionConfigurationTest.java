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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import com.google.cloud.discotoproto3converter.proto3.ConversionConfiguration;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Before;
import org.junit.Test;

import com.google.gson.Gson;

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

 boolean checkIdenticalJSON(String expected, String actual) {
  return ConversionConfiguration.FromJSON(expected).publicFieldsEqual(ConversionConfiguration.FromJSON(actual), true);
}

  @Test
  public void publicFieldsEqual() {
    System.out.printf("** publicFieldsEqual\n");
    Gson gson = new Gson();
    String referenceConfig = """
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
        assert checkIdenticalJSON(referenceConfig, referenceConfig);

    // Check that changing the order of the lists or maps doesn't affect equality.
    String variantConfig = """
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
        assert checkIdenticalJSON(referenceConfig, variantConfig);

    // Changing the schema makes the configs not equal
    variantConfig = """
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
        assertFalse(checkIdenticalJSON(referenceConfig, variantConfig));

    // Changing the locations makes the configs not equal
    variantConfig = """
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
        assertFalse(checkIdenticalJSON(referenceConfig, variantConfig));

  }

  @Test
  public void getMessageNameForPath() {
    String label = "readWriteWithoutAnyChanges";

    ConversionConfiguration config = ConversionConfiguration.FromJSON(inputConfig);

    assert "LakeInfo".equals(config.getMessageNameForPath("schemas.Lake.info"));
    assert "LakeInfo".equals(config.getMessageNameForPath(new String("schemas.BigLake.lakeInfo")));
    assert "PondInfo".equals(config.getMessageNameForPath(new String("schemas.Pond.info")));
    assert "PondInfo".equals(config.getMessageNameForPath("schemas.BigPond.pondInfo"));
  }

  @Test
  public void readWriteWithoutAnyChanges() {
    String label = "readWriteWithoutAnyChanges";

    ConversionConfiguration config = ConversionConfiguration.FromJSON(inputConfig);

    config.addInlineSchemaInstance("schemas.Pond.info", "PondInfo", "an initial schema");
    config.addInlineSchemaInstance("schemas.BigLake.lakeInfo", "LakeInfo", "an initial schema");
    config.addInlineSchemaInstance("schemas.Lake.info", "LakeInfo", "an initial schema");
    config.addInlineSchemaInstance("schemas.BigPond.pondInfo", "PondInfo", "an initial schema");
    String outputConfig = config.ToJSON();

    System.out.printf("** %s: input\n%s\n%s: output\n%s\n",
        label, inputConfig,
        label, outputConfig);
    assert checkIdenticalJSON(inputConfig, outputConfig);
  }

  @Test
  public void readWriteUsingSchemaTwice() {
    String label = "readWriteUsingSchemaTwice";

    ConversionConfiguration config = ConversionConfiguration.FromJSON(inputConfig);
    config.addInlineSchemaInstance("schemas.Pond.info", "PondInfo", "an initial schema");
    config.addInlineSchemaInstance("schemas.BigLake.lakeInfo", "LakeInfo", "an initial schema");
    config.addInlineSchemaInstance("schemas.Lake.info", "LakeInfo", "an initial schema");
    config.addInlineSchemaInstance("schemas.Lake.info", "LakeInfo", "an initial schema"); // should cause error
    config.addInlineSchemaInstance("schemas.BigPond.pondInfo", "PondInfo", "an initial schema");
    System.out.printf("** %s: input\n%s\n", label, inputConfig);

    assertThrows(IllegalStateException.class,
        () -> config.ToJSON());
  }

  @Test
  public void readWriteNotUsingASchema() {
    String label = "readWriteUsingSchemaTwice";

    ConversionConfiguration config = ConversionConfiguration.FromJSON(inputConfig);
    config.addInlineSchemaInstance("schemas.Pond.info", "PondInfo", "an initial schema");
    config.addInlineSchemaInstance("schemas.Lake.info", "LakeInfo", "an initial schema");
    config.addInlineSchemaInstance("schemas.BigPond.pondInfo", "PondInfo", "an initial schema");
    // not using schemas.BigLake.lakeInfo should cause an error
    System.out.printf("** %s: input\n%s\n", label, inputConfig);

    assertThrows(IllegalStateException.class,
        () -> config.ToJSON());
  }

  @Test
  public void readWriteSplittingSchema() {
    String label = "readWriteSplittingSchema";

    ConversionConfiguration config = ConversionConfiguration.FromJSON(inputConfig);
    config.addInlineSchemaInstance("schemas.Pond.info", "FirstPondInfo", "current schema");
    config.addInlineSchemaInstance("schemas.BigLake.lakeInfo", "AugmentedLakeInfo", "current schema");
    config.addInlineSchemaInstance("schemas.Lake.info", "LakeInfo", "current schema");
    config.addInlineSchemaInstance("schemas.BigPond.pondInfo", "SecondPondInfo", "current schema");
    String outputConfig = config.ToJSON();

    String expectedConfig = """
        {
       "converterVersion": "some-identifier",
       "apiVersion": "gamma",
       "discoveryRevision": "20250204",
       "inlineSchemas": [
          {
            "schema": "current schema",
            "locations": {
           "SecondPondInfo": ["schemas.BigPond.pondInfo"],
              "FirstPondInfo": ["schemas.Pond.info"],
              "AugmentedLakeInfo": ["schemas.BigLake.lakeInfo"],
              "LakeInfo": ["schemas.Lake.info"]
            }
           }
       ]
       }
    """;
    System.out.printf("** %s: input\n%s\n%s: output\n%s\n%s: expected\n%s\n",
        label, inputConfig,
        label, outputConfig,
        label, expectedConfig);
    assert checkIdenticalJSON(expectedConfig, outputConfig);
  }

  @Test
  public void readWriteAddingSchema() {
    String label = "readWriteAddingSchema";

    ConversionConfiguration config = ConversionConfiguration.FromJSON(inputConfig);
    config.addInlineSchemaInstance("schemas.Pond.info", "PondInfo", "current schema");
    config.addInlineSchemaInstance("schemas.BigLake.lakeInfo", "LakeInfo", "current schema");
    config.addInlineSchemaInstance("schemas.Lake.info", "LakeInfo", "current schema");
    config.addInlineSchemaInstance("schemas.BigPond.pondInfo", "PondInfo", "current schema");
    config.addInlineSchemaInstance("schemas.River.info", "RiverInfo", "new schema");
    String outputConfig = config.ToJSON();

    String expectedConfig =         """
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

    System.out.printf("** %s: input\n%s\n%s: output\n%s\n%s: expected\n%s\n",
        label, inputConfig,
        label, outputConfig,
        label, expectedConfig);
    assert checkIdenticalJSON(expectedConfig, outputConfig);
  }

   @Test
  public void readWriteAddingSchemaStrnigVairable() {
    String label = "readWriteAddingSchema";

    ConversionConfiguration config = ConversionConfiguration.FromJSON(inputConfig);
    // This first call uses a non-literal to ensure we are doing the correct comparisons under the hood.
    config.addInlineSchemaInstance("schemas.Pond.info", "PondInfo", new String("current schema"));
    config.addInlineSchemaInstance("schemas.BigLake.lakeInfo", "LakeInfo", "current schema");
    config.addInlineSchemaInstance("schemas.Lake.info", "LakeInfo", "current schema");
    config.addInlineSchemaInstance("schemas.BigPond.pondInfo", "PondInfo", "current schema");
    config.addInlineSchemaInstance("schemas.River.info", "RiverInfo", "new schema");
    String outputConfig = config.ToJSON();

    String expectedConfig =         """
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

    System.out.printf("** %s: input\n%s\n%s: output\n%s\n%s: expected\n%s\n",
        label, inputConfig,
        label, outputConfig,
        label, expectedConfig);
    assert checkIdenticalJSON(expectedConfig, outputConfig);
  }


  @Test
  public void readWriteWithoutUsingFieldSchema() {
    String label = "readWriteWithoutUsingFieldSchema";
    ConversionConfiguration config = ConversionConfiguration.FromJSON(inputConfig);
    config.addInlineSchemaInstance("schemas.Pond.info", "PondInfo", "current schema");
    config.addInlineSchemaInstance("schemas.BigLake.lakeInfo", "LakeInfo", "current schema");
    config.addInlineSchemaInstance("schemas.Lake.info", "LakeInfo", "current schema");
    // We omit BigPond

    System.out.printf("** %s: input\n%s\n", label, inputConfig);

    assertThrows(IllegalStateException.class,
        () -> config.ToJSON());

  }

  // TODO: remember to accumulate errors and throw at the end with the list of errors with "\n".join(errorList)

  /*
  @Test
  public void readWriteWithoutUsage() {
    ConversionConfiguration config = ConversionConfiguration.FromJSON(inputConfig);
assertEquals(inputConfig, config.ToJSON());
  }
  */
}

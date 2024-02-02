/*
 * Copyright 2024 Google LLC
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
import static org.junit.Assert.assertThrows;

import java.util.Map;
import org.junit.Test;

public class ConverterAppTest {

  @Test
  public void parseArgsFull() {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Map<String, String> parsedArgs =
        app.parseArgs(
            new String[] {
              "--discovery_doc_path=alpha",
              "--previous_proto_file_path=beta",
              "--output_file_path=gamma",
              "--service_ignorelist=delta",
              "--message_ignorelist=epsilon",
              "--relative_link_prefix=zeta",
              "--enums_as_strings=eta",
              "--output_comments=theta"
            });

    assertEquals("alpha", parsedArgs.get("--discovery_doc_path"));
    assertEquals("beta", parsedArgs.get("--previous_proto_file_path"));
    assertEquals("gamma", parsedArgs.get("--output_file_path"));
    assertEquals("delta", parsedArgs.get("--service_ignorelist"));
    assertEquals("epsilon", parsedArgs.get("--message_ignorelist"));
    assertEquals("zeta", parsedArgs.get("--relative_link_prefix"));
    assertEquals("eta", parsedArgs.get("--enums_as_strings"));
    assertEquals("theta", parsedArgs.get("--output_comments"));
  }

  @Test
  public void parseArgsDefault() {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    Map<String, String> parsedArgs =
        app.parseArgs(
            new String[] {
              "--discovery_doc_path=alpha",
              "--previous_proto_file_path=beta",
              "--output_file_path=gamma",
              "--relative_link_prefix=zeta"
            });

    assertEquals("alpha", parsedArgs.get("--discovery_doc_path"));
    assertEquals("beta", parsedArgs.get("--previous_proto_file_path"));
    assertEquals("gamma", parsedArgs.get("--output_file_path"));
    assertEquals("", parsedArgs.get("--service_ignorelist"));
    assertEquals("", parsedArgs.get("--message_ignorelist"));
    assertEquals("zeta", parsedArgs.get("--relative_link_prefix"));
    assertEquals("false", parsedArgs.get("--enums_as_strings"));
    assertEquals("true", parsedArgs.get("--output_comments"));
  }

  @Test
  public void parseArgsUnrecognized() {
    DiscoToProto3ConverterApp app = new DiscoToProto3ConverterApp();
    assertThrows(
        java.lang.IllegalArgumentException.class,
        () ->
            app.parseArgs(
                new String[] {
                  "--discovery_doc_path=alpha",
                  "--unsupported_arg=omega",
                  "--previous_proto_file_path=beta",
                  "--output_file_path=gamma",
                  "--relative_link_prefix=zeta"
                }));
  }
}

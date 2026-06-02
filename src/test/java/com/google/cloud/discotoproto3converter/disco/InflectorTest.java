/*
 * Copyright 2026 Google LLC
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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class InflectorTest {

  @Test
  public void singularize_regularPlurals() {
    assertEquals("Address", Inflector.singularize("Addresses"));
    assertEquals("Wolf", Inflector.singularize("Wolves"));
    assertEquals("Category", Inflector.singularize("Categories"));
    assertEquals("Dish", Inflector.singularize("Dishes"));
    assertEquals("Buzz", Inflector.singularize("Buzzes"));
  }

  @Test
  public void singularize_xesAndChesEndings() {
    assertEquals("Prefix", Inflector.singularize("Prefixes"));
    assertEquals("Box", Inflector.singularize("Boxes"));
    assertEquals("Church", Inflector.singularize("Churches"));
  }
}

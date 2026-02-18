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
import static org.junit.Assert.assertThrows;

import com.google.auto.value.AutoValue;
import org.junit.Test;

public class MethodTest {

  @Test
  public void accomodatePathSubresources_noToken() {
    // path does not match regex
    assertEquals("projects/p1/zones/z1", Method.accomodatePathSubresources("projects/p1/zones/z1", "projects/p1/zones/z1"));
    assertEquals("anything", Method.accomodatePathSubresources("no-token", "anything"));
  }

  @Test
  public void accomodatePathSubresources_multipleTokens() {
    // path matches regex more than once
    assertThrows(IllegalArgumentException.class, () -> Method.accomodatePathSubresources("projects/{+project}/zones/{+zone}", "projects/p1/zones/z1"));
  }

  @Test
  public void accomodatePathSubresources_invalidFlatPathPrefix() {
    // path matches once, but flatPath doesn't match prefix
    assertThrows(IllegalArgumentException.class, () -> Method.accomodatePathSubresources("projects/{+project}/zones", "other/p1/zones"));
  }

  @Test
  public void accomodatePathSubresources_invalidFlatPathSuffix() {
    // path matches once, but flatPath doesn't match suffix
    assertThrows(IllegalArgumentException.class, () -> Method.accomodatePathSubresources("projects/{+project}/zones", "projects/p1/other"));
  }

  @Test
  public void accomodatePathSubresources_flatPathTooShort() {
    // path matches once, but flatPath length < prefix + suffix
    // prefix="prefix/", suffix="/suffix", combined length = 7 + 7 = 14
    // flatPath="prefix/suffix" has length 13.
    assertThrows(IllegalArgumentException.class, () -> Method.accomodatePathSubresources("prefix/{+name}/suffix", "prefix/suffix"));
  }

  @Test
  public void accomodatePathSubresources_standard() {
    // Normal variable expansion
    assertEquals("projects/{project=my-project}/zones", Method.accomodatePathSubresources("projects/{+project}/zones", "projects/my-project/zones"));
  }

  @Test
  public void accomodatePathSubresources_withSubresourceBraces() {
    // Subresource in flatPath contains {foo} tokens which should be replaced by *
    assertEquals("projects/{project=*}/zones", Method.accomodatePathSubresources("projects/{+project}/zones", "projects/{project}/zones"));
  }

  @Test
  public void accomodatePathSubresources_complexSubresource() {
    // Multiple {} tokens in subresource
    assertEquals("{name=a*c*e}", Method.accomodatePathSubresources("{+name}", "a{b}c{d}e"));
  }

  @Test
  public void accomodatePathSubresources_emptyPrefixSuffix() {
    // Path is just the token
    assertEquals("{name=foo/bar}", Method.accomodatePathSubresources("{+name}", "foo/bar"));
  }

  @Test
  public void accomodatePathSubresources_multipleSubresourceBraces() {
    // Mix of literal and {} in subresource
    assertEquals("prefix/{name=a*c*e}/suffix", Method.accomodatePathSubresources("prefix/{+name}/suffix", "prefix/a{b}c{d}e/suffix"));
  }

  @Test
  public void accomodatePathSubresources_tokenWithNumbers() {
    // Alphanumeric token
    assertEquals("v1/{var123=val}/v2", Method.accomodatePathSubresources("v1/{+var123}/v2", "v1/val/v2"));
  }

  @Test
  public void accomodatePathSubresources_camelCaseToken() {
    // camelCase token should be converted to snake_case
    assertEquals("projects/{my_project=foo}/zones", Method.accomodatePathSubresources("projects/{+myProject}/zones", "projects/foo/zones"));
  }

  @Test
  public void accomodatePathSubresources_noMatchDueToHyphen() {
    // {+foo-bar} does not match {+[a-zA-Z0-9]+}
    assertEquals("projects/{+foo-bar}/zones", Method.accomodatePathSubresources("projects/{+foo-bar}/zones", "projects/{+foo-bar}/zones"));
  }
}

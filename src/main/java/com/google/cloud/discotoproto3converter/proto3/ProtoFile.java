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
package com.google.cloud.discotoproto3converter.proto3;

public class ProtoFile {
  private static final String LICENSE =
      "// Copyright %s Google LLC\n"
          + "//\n"
          + "// Licensed under the Apache License, Version 2.0 (the \"License\");\n"
          + "// you may not use this file except in compliance with the License.\n"
          + "// You may obtain a copy of the License at\n"
          + "//\n"
          + "//     http://www.apache.org/licenses/LICENSE-2.0\n"
          + "//\n"
          + "// Unless required by applicable law or agreed to in writing, software\n"
          + "// distributed under the License is distributed on an \"AS IS\" BASIS,\n"
          + "// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
          + "// See the License for the specific language governing permissions and\n"
          + "// limitations under the License.\n";

  private final String discoFileName;
  private final String discoName;
  private final String discoVersion;
  private final String discoRevision;
  private final String protoPkg;
  private final String protoPkgVersion;

  public ProtoFile(
      String discoFileName,
      String discoName,
      String discoVersion,
      String discoRevision,
      String protoPkg,
      String protoPkgVersion) {
    this.discoFileName = discoFileName;
    this.discoName = discoName;
    this.discoVersion = discoVersion;
    this.discoRevision = discoRevision;
    this.protoPkg = protoPkg;
    this.protoPkgVersion = protoPkgVersion;
  }

  public String getLicense() {
    return String.format(LICENSE, discoRevision.substring(0, 4));
  }

  public String getDiscoFileName() {
    return discoFileName;
  }

  public String getDiscoName() {
    return discoName;
  }

  public String getDiscoVersion() {
    return discoVersion;
  }

  public String getDiscoRevision() {
    return discoRevision;
  }

  public String getProtoPkg() {
    return protoPkg;
  }

  public String getProtoPkgVersion() {
    return protoPkgVersion;
  }
}

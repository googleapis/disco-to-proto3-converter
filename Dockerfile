# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM maven:3.9.12-eclipse-temurin-21 AS builder
WORKDIR /repo

# --- Optimized Caching Strategy ---
# 1. Copy only the pom.xml to leverage Docker's layer caching.
#    Dependencies will only be re-downloaded if pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline

# 2. Copy source tree.
COPY . .

# A clear, concise check that will fail the build if .git is not a directory.
RUN test -d /repo/.git || (echo "ERROR: .git is not a directory. Build from a full repo clone, not a worktree." && ls -la  /repo && exit 1)

RUN mvn package

FROM eclipse-temurin:25
WORKDIR /app

# Copy the built JAR from the builder stage.
COPY --from=builder /repo/discovery-converter-app.jar ./
# To keep the Docker image as small as needed, we omit the
# single-purpose converters `disco-to-proto3-converter-app.jar`,
# `service-config-generator-app.jar`, and
# `gapic-yaml-generator-app.jar`. They can be added when needed.

# Define the entrypoint to run the application.
ENTRYPOINT ["java", "-jar", "discovery-converter-app.jar"]

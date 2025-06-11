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

FROM maven:3.9.9-eclipse-temurin-21 AS builder
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

FROM eclipse-temurin:21
WORKDIR /app

# --- Robust Artifact Handling ---
# Use an ARG to define the JAR path with a wildcard to avoid hardcoding versions.
ARG JAR_FILE=target/disco-to-proto3-converter-*-jar-with-dependencies.jar

# Copy the built JAR from the builder stage and give it a consistent name.
COPY --from=builder /repo/${JAR_FILE} disco-to-proto3-converter.jar

# Define the entrypoint to run the application.
ENTRYPOINT ["java", "-jar", "disco-to-proto3-converter.jar"]

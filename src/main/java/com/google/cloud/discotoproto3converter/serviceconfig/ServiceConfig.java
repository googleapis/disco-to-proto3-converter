/*
 * Copyright 2021 Google LLC
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
package com.google.cloud.discotoproto3converter.serviceconfig;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

public class ServiceConfig {
  @JsonInclude(Include.NON_NULL)
  public static class MethodConfig {
    private final List<Name> name = new ArrayList<>();
    private final String timeout;
    private final RetryPolicy retryPolicy;

    public MethodConfig(String timeout, RetryPolicy retryPolicy) {
      this.timeout = timeout;
      this.retryPolicy = retryPolicy;
    }

    public List<Name> getName() {
      return name;
    }

    public String getTimeout() {
      return timeout;
    }

    public RetryPolicy getRetryPolicy() {
      return retryPolicy;
    }
  }

  public static class Name {
    private final String service;
    private final String method;

    public Name(String service, String method) {
      this.service = service;
      this.method = method;
    }

    public String getService() {
      return service;
    }

    public String getMethod() {
      return method;
    }
  }

  public static class RetryPolicy {
    private final String initialBackoff;
    private final String maxBackoff;
    private final double backoffMultiplier;
    private final List<String> retryableStatusCodes;

    public RetryPolicy(
        String initialBackoff,
        String maxBackoff,
        double backoffMultiplier,
        List<String> retryableStatusCodes) {
      this.initialBackoff = initialBackoff;
      this.maxBackoff = maxBackoff;
      this.backoffMultiplier = backoffMultiplier;
      this.retryableStatusCodes = ImmutableList.copyOf(retryableStatusCodes);
    }

    public String getInitialBackoff() {
      return initialBackoff;
    }

    public String getMaxBackoff() {
      return maxBackoff;
    }

    public double getBackoffMultiplier() {
      return backoffMultiplier;
    }

    public List<String> getRetryableStatusCodes() {
      return retryableStatusCodes;
    }
  }

  private final List<MethodConfig> methodConfig = new ArrayList<>();

  public List<MethodConfig> getMethodConfig() {
    return methodConfig;
  }
}

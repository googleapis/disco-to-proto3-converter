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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.discotoproto3converter.proto3.ConverterWriter;
import com.google.cloud.discotoproto3converter.proto3.GrpcMethod;
import com.google.cloud.discotoproto3converter.proto3.GrpcService;
import com.google.cloud.discotoproto3converter.proto3.Option;
import com.google.cloud.discotoproto3converter.proto3.ProtoFile;
import com.google.cloud.discotoproto3converter.serviceconfig.ServiceConfig.MethodConfig;
import com.google.cloud.discotoproto3converter.serviceconfig.ServiceConfig.Name;
import com.google.cloud.discotoproto3converter.serviceconfig.ServiceConfig.RetryPolicy;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Optional;

public class ServiceConfigWriter implements ConverterWriter {
  @Override
  public void writeToFile(PrintWriter writer, ProtoFile protoFile, boolean outputComments)
      throws IOException {

    ServiceConfig serviceConfig = new ServiceConfig();
    String protoPkg = protoFile.getMetadata().getProtoPkg();

    RetryPolicy idempotentRetryPolicy =
        new RetryPolicy("0.100s", "60s", 1.3D, Arrays.asList("DEADLINE_EXCEEDED", "UNAVAILABLE"));
    MethodConfig idempotentMethodConfig = new MethodConfig("600s", idempotentRetryPolicy);
    MethodConfig nonidempotentMethodConfig = new MethodConfig("600s", null);

    for (GrpcService service : protoFile.getServices().values()) {
      for (GrpcMethod method : service.getMethods()) {
        Optional<Option> opt =
            method.getOptions().stream()
                .filter(o -> o.getName().equals("google.api.http"))
                .findFirst();

        Name name = new Name(protoPkg + "." + service.getName(), method.getName());
        if (opt.isPresent() && opt.get().getProperties().containsKey("get")) {
          idempotentMethodConfig.getName().add(name);
        } else {
          nonidempotentMethodConfig.getName().add(name);
        }
      }
    }

    serviceConfig.getMethodConfig().add(idempotentMethodConfig);
    serviceConfig.getMethodConfig().add(nonidempotentMethodConfig);

    ObjectMapper mapper = new ObjectMapper();

    String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(serviceConfig);
    writer.println(json);
  }
}

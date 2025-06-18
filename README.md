# Google Discovery Document to Proto3 Converter

## Quick Start

### Requirements
To build and run this tool, you will need the following tools:
- JDK 8+
- Maven 3.3.9+

### Build
To build the standalone jar, run the following command from the repository root:
```sh
mvn package
```

### Test
To run the unit tests, execute the following command:
```sh
mvn test
```

### Format files
To automatically format the Java source files, use the following Bazel command:

```sh
bazel run :google_java_format --enable_workspace
```

### Run
After performing the build, you obtain the API protocol buffer file and GAPIC
configuration files derived from the sample `compute.v1.json` Discovery file
(included in this repository) by running the following command from the
repository root:

```sh
java \
  -jar target/target/discovery-converter-app.jar \
  --discovery_doc_path=src/test/resources/google/cloud/compute/v1/compute.v1.json \
  --output_file_path=google/cloud/compute/v1/compute \
  --input_config_path=src/test/resources/google/cloud/compute/v1/compute.v1.config.input.json \
  --output_config_path=google/cloud/compute/v1/compute.v1.config.output.json \
  --enums_as_strings=True
```

Check the `google/cloud/compute/v1` directory for the resulting `compute.proto`,
`compute_grpc_service_config.json`, and `compute_gapic.yaml` files.

#### (Alternative) Generate each file individually
You can also generate each of the files above individually by using distinct generator binaries for each.

* To generate just `compute.proto`, run:

```sh
java \
  -jar target/target/disco-to-proto3-converter-app.jar \
  --discovery_doc_path=src/test/resources/google/cloud/compute/v1/compute.v1.json \
  --output_file_path=google/cloud/compute/v1/compute.proto \
  --input_config_path=src/test/resources/google/cloud/compute/v1/compute.v1.config.input.json \
  --output_config_path=google/cloud/compute/v1/compute.v1.config.output.json \
  --enums_as_strings=True
```

To generate just `compute_grpc_service_config.json`, run:

```sh
java \
  -jar target/service-config-generator-app.jar \
  --discovery_doc_path=src/test/resources/google/cloud/compute/v1/compute.v1.json \
  --output_file_path=google/cloud/compute/v1/compute_grpc_service_config.json \
  --input_config_path=src/test/resources/google/cloud/compute/v1/compute.v1.config.input.json \
  --output_config_path=google/cloud/compute/v1/compute.v1.config.output.json \
  --enums_as_strings=True
```

To generate just `compute_gapic.yaml`, run:

```sh
java \
  -jar target/gapic-yaml-generator-app.jar \
  --discovery_doc_path=src/test/resources/google/cloud/compute/v1/compute.v1.json \
  --output_file_path=google/cloud/compute/v1/compute_gapic.yaml \
  --input_config_path=src/test/resources/google/cloud/compute/v1/compute.v1.config.input.json \
  --output_config_path=google/cloud/compute/v1/compute.v1.config.output.json \
  --enums_as_strings=True
```


### Docker
You can package the converter in a Docker image and run it as follows:

* To build the container, use an invocation like

   ```sh
   docker build --no-cache -t disco-converter:test .
   ```

* If you need to inspect the container, run it with a shell entrypoint:

   ```sh
    docker run --entrypoint /bin/bash -it disco-converter:test
   ```

* To run the container in a manner similar to above, mount the local directories
  containing your input and output files. For example

   ```sh
   docker run -v $(pwd):/apis converter:test \
     --discovery_doc_path=/apis/src/test/resources/google/cloud/compute/v1/compute.v1.json \
     --output_file_path=/apis/google/cloud/compute/v1/compute \
     --input_config_path=/apis/src/test/resources/google/cloud/compute/v1/compute.v1.config.input.json \
     --output_config_path=/apis/google/cloud/compute/v1/compute.v1.config.output.json  \
     --enums_as_strings=True
   ```



### Bazel
The converter can also be used from Bazel via the `proto_from_disco` bazel rule
like the following:

```bzl
load(
    "@com_google_disco_to_proto3_converter//rules_gapic:disco_to_proto.bzl",
    "proto_from_disco",
)

proto_from_disco(
    name = "compute",
    src = "//:src/test/resources/compute.v1.json",
)
```

**This is not an officially supported Google product**

# Google Discovery Document to Proto3 Converter

## Quick Start

### Requirements
To build and run this tool you will need the following tools:
- JDK 8+
- Maven 3.3.9+

### Build
To build the standalone jar run the following command from the repository root:
```sh
mvn package
```

### Test
To run the unit tests execute the following command:
```sh
mvn test
```

### Run
After performing the build, to run the converter using `compute.v1.json` as a 
sample input (included in this repository) run the following command from the 
repository root:
```sh
java \
  -jar target/disco-to-proto3-converter-0.0.1-SNAPSHOT-jar-with-dependencies.jar \
  --discovery_doc_path=src/test/resources/google/cloud/compute/v1/compute.v1.json \
  --output_file_path=google/cloud/compute/v1/compute.proto
``` 

Check the `google/cloud/compute/v1` directory for the converted `compute.proto` 
file.

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

# Google Discovery Document to Proto3 Converter

## Quick Start

## Requirements
To build and run this tool you will need the following tools:
- JDK 8+
- Maven 3.3.9+

### Build
To build the standalone jar run the following command from the repository root:
```
mvn package
```

### Run
After performing the build, to run the converter using `compute.v1.json` as a 
sample input (included in this repository) run the following command from the 
repository root:
```
java \
  -jar target/disco-to-proto3-converter-0.0.1-SNAPSHOT-jar-with-dependencies.jar \
  --discovery_doc_path=src/test/resources/compute.v1.json \
  --output_root_path=. \
  --output_file_name=compute.proto
``` 

Check the `google/cloud/compute/v1` directory for the converted `compute.proto` 
file.
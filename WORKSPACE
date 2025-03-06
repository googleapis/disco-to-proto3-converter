workspace(name = "com_google_disco_to_proto3_converter")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("//:repository_rules.bzl", "com_google_disco_to_proto3_converter_properties")

com_google_disco_to_proto3_converter_properties(
    name = "com_google_disco_to_proto3_converter_properties",
    file = "//:pom.xml",
)

load("//:repositories.bzl", "com_google_disco_to_proto3_converter_repositories")

com_google_disco_to_proto3_converter_repositories()

register_toolchains("//:error_prone_warnings_toolchain_java17_definition")

# Copyright 2020 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.AGE.

def read_property(prop, cur_prop_val, prop_names):
    for i in range(len(prop_names)):
        prop_name = prop_names[i]
        if prop.startswith("<%s>" % prop_name) and prop.endswith("</%s>" % prop_name):
            pv = cur_prop_val.split(":")
            pv[i] = prop[len(prop_name) + 2:len(prop) - (len(prop_name) + 3)].strip()
            return ":".join(pv)
    return cur_prop_val

def construct_property_key(prefix, dep_val):
    key_suffix = dep_val[:dep_val.rfind(":")]
    for ch in [":", "-", "."]:
        key_suffix = key_suffix.replace(ch, "_")
    return "%s%s" % (prefix, key_suffix)

def read_properties_from_pom_xml(props, parent_tags, property_tags):
    props_as_map = {}
    tag_index = 0
    cur_dep_val = "::"
    for prop in props:
        p = prop.strip()
        if len(p) <= 0 or p.startswith("<!--"):
            continue
        if tag_index < len(parent_tags) and ("<%s>" % parent_tags[tag_index]) == p:
            tag_index += 1
            if tag_index == len(parent_tags):
                cur_dep_val = "::"
            continue
        elif tag_index > 0 and ("</%s>" % parent_tags[tag_index - 1]) == p:
            if tag_index == len(parent_tags):
                props_as_map[construct_property_key("maven.", cur_dep_val)] = cur_dep_val
            tag_index -= 1
            continue

        if tag_index != len(parent_tags):
            continue

        cur_dep_val = read_property(p, cur_dep_val, property_tags)

    return props_as_map

def _com_google_disco_to_proto3_converter_properties_impl(ctx):
    props_path = ctx.path(ctx.attr.file)
    result = ctx.execute(["cat", props_path])

    if result.return_code != 0:
        fail("Could not load dependencies from properties file, error_code %s" + str(result.return_code))

    props = result.stdout.splitlines()
    props_as_map = read_properties_from_pom_xml(
        props,
        ["dependencies", "dependency"],
        ["groupId", "artifactId", "version"],
    )

    props_name = ctx.attr.file.name
    dependencies_bzl = """
# DO NOT EDIT. This file was generated from {properties_file}.
PROPERTIES = {props_as_map}
     """.format(
        properties_file = props_name,
        props_as_map = str(props_as_map),
    )

    ctx.file("BUILD.bazel", "")
    ctx.file("%s.bzl" % props_name, dependencies_bzl)

com_google_disco_to_proto3_converter_properties = repository_rule(
    implementation = _com_google_disco_to_proto3_converter_properties_impl,
    attrs = {
        "file": attr.label(),
    },
    local = True,
)

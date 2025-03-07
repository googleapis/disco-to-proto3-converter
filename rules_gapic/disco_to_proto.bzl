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
# limitations under the License.

def _set_args(arg, arg_name, args, inputs = None):
    if not arg:
        return
    args.append("%s%s" % (arg_name, arg.files.to_list()[0].path if hasattr(arg, "files") else arg))
    if inputs != None:
        inputs.append(arg.files.to_list()[0])

def _proto_from_disco_impl(ctx):
    arguments = []
    inputs = []

    attr = ctx.attr
    _set_args(attr.src, "--discovery_doc_path=", arguments, inputs)
    _set_args(attr.previous_proto, "--previous_proto_file_path=", arguments, inputs)
    _set_args(",".join(attr.service_ignorelist), "--service_ignorelist=", arguments)
    _set_args(",".join(attr.message_ignorelist), "--message_ignorelist=", arguments)
    _set_args(attr.relative_link_prefix, "--relative_link_prefix=", arguments)
    _set_args(attr.enums_as_strings, "--enums_as_strings=", arguments)
    _set_args(attr.output_comments, "--output_comments=", arguments)
    _set_args(attr.input_config_path, "--input_config_path=", arguments, inputs)

    converter = ctx.executable.converter
    ctx.actions.run(
        inputs = inputs,
        outputs = [ctx.outputs.output, ctx.outputs.output_config],
        arguments = arguments + [
            "--output_file_path=%s" % ctx.outputs.output.path,
            "--output_config_path=%s" % (ctx.outputs.output_config.path if attr.output_config_suffix else ""),
        ],
        progress_message = "%s: `%s %s`" % (ctx.label, converter.path, " ".join(arguments)),
        executable = converter,
    )

proto_from_disco = rule(
    attrs = {
        "src": attr.label(mandatory = True, allow_single_file = True),
        "previous_proto": attr.label(mandatory = False, allow_single_file = True),
        "service_ignorelist": attr.string_list(allow_empty = True, default = []),
        "message_ignorelist": attr.string_list(allow_empty = True, default = []),
        "relative_link_prefix": attr.string(mandatory = False, default = ""),
        "enums_as_strings": attr.bool(mandatory = False, default = False),
        "output_comments": attr.bool(mandatory = False, default = False),
        "converter": attr.label(
            default = Label("//:disco_to_proto3_converter"),
            executable = True,
            cfg = "host",
        ),
        "extension": attr.string(mandatory = False, default = ".proto"),
        "input_config_path": attr.label(mandatory = False, allow_single_file = True),
        # provide an empty string to omit the output config
        "output_config_suffix": attr.string(mandatory = False, default = ".config.out"),
    },
    outputs = {
        "output": "%{name}%{extension}",
        "output_config": "%{name}%{output_config_suffix}.json",
    },
    implementation = _proto_from_disco_impl,
)

def grpc_service_config_from_disco(
        name,
        src,
        input_config_path = None,
        output_config_suffix = ".config.out",
        previous_proto = None,
        service_ignorelist = None,
        message_ignorelist = None,
        relative_link_prefix = None,
        visibility = None,
        **kwargs):
    proto_from_disco(
        name = name,
        src = src,
        input_config_path = input_config_path,
        output_config_suffix = output_config_suffix,
        previous_proto = previous_proto,
        service_ignorelist = service_ignorelist,
        message_ignorelist = message_ignorelist,
        relative_link_prefix = relative_link_prefix,
        converter = Label("//:service_config_generator"),
        extension = ".json",
        visibility = visibility,
    )

def gapic_yaml_from_disco(
        name,
        src,
        input_config_path = None,
        output_config_suffix = ".config.out",
        previous_proto = None,
        service_ignorelist = None,
        message_ignorelist = None,
        relative_link_prefix = None,
        visibility = None,
        **kwargs):
    proto_from_disco(
        name = name,
        src = src,
        input_config_path = input_config_path,
        output_config_suffix = output_config_suffix,
        previous_proto = previous_proto,
        service_ignorelist = service_ignorelist,
        message_ignorelist = message_ignorelist,
        relative_link_prefix = relative_link_prefix,
        converter = Label("//:gapic_yaml_generator"),
        extension = ".yaml",
        visibility = visibility,
    )

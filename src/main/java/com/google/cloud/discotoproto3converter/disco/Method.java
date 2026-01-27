/*
 * Copyright 2020 Google LLC
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
package com.google.cloud.discotoproto3converter.disco;

import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Adapted from its counterpart in gapic-generator.
 *
 * <p>A representation of a Discovery Document method.
 *
 * <p>Note that this class is not necessarily a 1-1 mapping of the official specification.
 */
@AutoValue
public abstract class Method implements Comparable<Method>, Node {

  /**
   * Returns a method constructed from root.
   *
   * @param root the root node to parse.
   * @return a method.
   */
  public static Method from(DiscoveryNode root, Node parent) {
    String description = root.getString("description");
    String httpMethod = root.getString("httpMethod");
    String id = root.getString("id");
    String path = root.getString("path");
    String flatPath = root.has("flatPath") ? root.getString("flatPath") : path;
    String apiVersion = root.getString("apiVersion");

    DiscoveryNode parametersNode = root.getObject("parameters");
    Map<String, Schema> parameters = new LinkedHashMap<>();
    Map<String, Schema> queryParams = new LinkedHashMap<>();
    Map<String, Schema> pathParams = new LinkedHashMap<>();

    flatPath = allowSlashedParent(path, flatPath);

    for (String name : root.getObject("parameters").getFieldNames()) {
      Schema schema = Schema.from(parametersNode.getObject(name), name, null);
      // TODO: Remove these checks once we're sure that parameters can't be objects/arrays.
      // This is based on the assumption that these types can't be serialized as a query or path
      // parameter.
      Preconditions.checkState(schema.type() != Schema.Type.ANY);
      Preconditions.checkState(schema.type() != Schema.Type.ARRAY);
      Preconditions.checkState(schema.type() != Schema.Type.OBJECT);
      parameters.put(name, schema);
      if (schema.location().toLowerCase().equals("path")) {
        pathParams.put(name, schema);
      } else if (schema.location().toLowerCase().equals("query")) {
        queryParams.put(name, schema);
      }
    }

    List<String> requiredParamNames =
        root.getArray("parameterOrder")
            .getElements()
            .stream()
            .map(DiscoveryNode::asText)
            .collect(Collectors.toList());

    Schema request = Schema.from(root.getObject("request"), "request", null);
    if (request.reference().isEmpty()) {
      request = null;
    }
    Schema response = Schema.from(root.getObject("response"), "response", null);
    if (response.reference().isEmpty()) {
      response = null;
    }
    List<String> scopes = new ArrayList<>();
    for (DiscoveryNode scopeNode : root.getArray("scopes").getElements()) {
      scopes.add(scopeNode.asText());
    }
    boolean supportsMediaDownload = root.getBoolean("supportsMediaDownload");
    boolean supportsMediaUpload = root.getBoolean("supportsMediaUpload");

    Method thisMethod =
        new AutoValue_Method(
            description,
            flatPath,
            httpMethod,
            id,
            parameters,
            path,
            pathParams,
            queryParams,
            requiredParamNames,
            request,
            response,
            scopes,
            supportsMediaDownload,
            supportsMediaUpload,
            apiVersion);

    thisMethod.parent = parent;
    if (request != null) {
      request.setParent(thisMethod);
    }
    if (response != null) {
      response.setParent(thisMethod);
    }
    for (Schema schema : parameters.values()) {
      schema.setParent(thisMethod);
    }
    return thisMethod;
  }

    public static String allowSlashedParent(String path, String flatPath) {
	// This is temporary, special-case code to accept `parentName` in an RFC6570 Level 2-compliant style, i.e. `{+parentName}`. If `path` contains exactly one instance, `path` is returned with the `+` removed. If there is more than one instance, or `{+` appears in some other context, returns an error. Otherwise, returns `flatPath`.
	if (path == null) {
	    return flatPath; 
	}

	int firstIndex = path.indexOf("{+");

	// Case 1: Prefix not found
	if (firstIndex == -1) {
	    return flatPath;
	}

	// Check for a second occurrence
	int lastIndex = path.lastIndexOf("{+");

	// Case 2: Prefix appears more than once
	if (firstIndex != lastIndex) {
	    throw new IllegalArgumentException("The substring '{+' appears multiple times in path.");
	}

	// Check for full special-case substring
	int parentIndex = path.lastIndexOf("{+parentName}");

	// Case 3: Check that the prefix is part of the special-case substring.
	if (parentIndex == -1) {
	    throw new IllegalArgumentException("The substring '{+' is not part of '{+parentName}'.");
	}

	// Case 3: Substring appears exactly once
	// Since we verified there is only one occurrence, .replace() is safe here.
	return path.replace("{+parentName}", "{parentName}");
    }

  @Override
  public int compareTo(Method other) {
    return id().compareTo(other.id());
  }

  /** @return the parent Node. */
  private Node parent;

  void setParent(Node parent) {
    this.parent = parent;
  }

  @Override
  public Node parent() {
    return parent;
  }

  /** @return the description. */
  public abstract String description();

  /** @return the flat URI path of this REST method. */
  public abstract String flatPath();

  /** @return the HTTP method. */
  public abstract String httpMethod();

  /** @return the ID. This should be unique, within the context of the parent Document. */
  @Override
  public abstract String id();

  /** @return the map of parameter names to schemas. */
  public abstract Map<String, Schema> parameters();

  /** @return the URI path of this REST method. */
  public abstract String path();

  /** @return the list of path parameters. */
  public abstract Map<String, Schema> pathParams();

  /** @return the list of query parameters. */
  public abstract Map<String, Schema> queryParams();

  /** @return the list of required parameters. */
  public abstract List<String> requiredParamNames();

  /** @return the request's resource object schema, or null if none. */
  @Nullable
  public abstract Schema request();

  /** @return the response schema, or null if none. */
  @Nullable
  public abstract Schema response();

  /** @return the list of scopes. */
  public abstract List<String> scopes();

  /** @return whether or not the method supports media download. */
  public abstract boolean supportsMediaDownload();

  /** @return whether or not the method supports media upload. */
  public abstract boolean supportsMediaUpload();

  /** @return the API version for this method. */
  public abstract String apiVersion();

  /**
   * @return if the method acts on a set of resources whose size may be greater than 1, e.g. List
   *     methods.
   */
  public boolean isPluralMethod() {
    return parameters().containsKey("maxResults");
  }

  @Override
  public int hashCode() {
    return id().hashCode();
  }

  @Override
  public boolean equals(Object o2) {
    return (o2 instanceof Method && (id().equals(((Method) o2).id())));
  }

  public Document getDocument() {
    Node parent = this;
    while (!(parent instanceof Document) && parent != null) {
      parent = this.parent();
    }
    return (Document) parent;
  }
}

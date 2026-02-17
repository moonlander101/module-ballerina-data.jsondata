/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.data.jsondata.json.schema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluationContext {
    private final String instanceLocation;
    private final String schemaLocation;
    private final EvaluationContext parent;
    private final List<String> errors;
    private final Map<String, Object> annotations;

    public EvaluationContext() {
        this(null, "", "");
    }

    private EvaluationContext(EvaluationContext parent, String instanceLocation, String schemaLocation) {
        this.parent = parent;
        this.instanceLocation = instanceLocation;
        this.schemaLocation = schemaLocation;
        this.errors = parent != null ? parent.errors : new ArrayList<>();
        this.annotations = new HashMap<>();
    }

    public EvaluationContext createChildContext(String instancePathSegment, String schemaPathSegment) {
        StringBuilder newInstanceLocation = new StringBuilder(instanceLocation);
        if (!instancePathSegment.isEmpty()) {
            if (!instanceLocation.isEmpty()) {
                newInstanceLocation.append("/");
            }
            newInstanceLocation.append(instancePathSegment);
        }

        StringBuilder newSchemaLocation = new StringBuilder(schemaLocation);
        if (!schemaPathSegment.isEmpty()) {
            if (!schemaLocation.isEmpty()) {
                newSchemaLocation.append("/");
            }
            newSchemaLocation.append(schemaPathSegment);
        }

        return new EvaluationContext(this, newInstanceLocation.toString(), newSchemaLocation.toString());
    }

    public void addError(String keywordName, String message) {
        errors.add(message);
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setAnnotation(String keywordName, Object value) {
        annotations.put(keywordName, value);
    }

    public Object getAnnotation(String keywordName) {
        return annotations.get(keywordName);
    }

    public String getInstanceLocation() {
        return instanceLocation;
    }
}

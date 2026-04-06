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

import java.net.URISyntaxException;
import java.util.*;
import java.net.URI;

public class EvaluationContext {
    private final String instanceLocation;
    private final String schemaLocation;
    private final EvaluationContext parent;
    private final List<String> errors;
    private final Map<String, Object> annotations;
    private final SchemaRegistry schemaRegistry;
    private final LinkedHashSet<URI> dynamicScope;

    public EvaluationContext() {
        this(null, "", "", null, new LinkedHashSet<>());
    }

    public EvaluationContext(SchemaRegistry schemaRegistry) {
        this(null, "", "", schemaRegistry, new LinkedHashSet<>());
    }

    private EvaluationContext(EvaluationContext parent, String instanceLocation, String schemaLocation,
                              SchemaRegistry schemaRegistry, LinkedHashSet<URI> dynamicScope) {
        this.parent = parent;
        this.instanceLocation = instanceLocation;
        this.schemaLocation = schemaLocation;
        this.errors = parent != null ? parent.errors : new ArrayList<>();
        this.annotations = new HashMap<>();
        this.schemaRegistry = schemaRegistry;
        this.dynamicScope = dynamicScope;
    }

    public void pushDynamicScope(URI resourceUri) {
        try {
            URI baseUri = new URI(resourceUri.getScheme(), resourceUri.getSchemeSpecificPart(), null);
            dynamicScope.add(baseUri);
        } catch (URISyntaxException e) {
            // If the URI is not valid, we can choose to ignore it or handle it as needed.
        }
    }

    public void popDynamicScope() {
        if (!dynamicScope.isEmpty()) {
            dynamicScope.removeLast();
        }
    }

    public LinkedHashSet<URI> getDynamicScope() {
        return dynamicScope;
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

        return new EvaluationContext(this, newInstanceLocation.toString(), newSchemaLocation.toString(), schemaRegistry, this.dynamicScope);
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

    public SchemaRegistry getSchemaRegistry() {
        return schemaRegistry;
    }

    public void moveToParentContext(String annotationKey) {
        if (parent == null) {
            return;
        }

        Object annotationValue = annotations.get(annotationKey);
        if (annotationValue == null) {
            return;
        }
        Object parentAnnotationValue = parent.getAnnotation(annotationKey);
        if (parentAnnotationValue == null) {
            parent.setAnnotation(annotationKey, annotationValue);
            return;
        }

        if (annotationValue instanceof Boolean childBool) {
            if (childBool) {
                parent.setAnnotation(annotationKey, true);
            }
            return;
        }

        if (parentAnnotationValue instanceof Boolean parentBool) {
            if (!parentBool) {
                parent.setAnnotation(annotationKey, annotationValue);
            }
            return;
        }

        if (annotationValue instanceof List<?> childAnnotationValues && parentAnnotationValue instanceof List<?> parentAnnotationValues) {
            List<Object> parentList = (List<Object>) parentAnnotationValues;
            for (Object value : childAnnotationValues) {
                if (!parentList.contains(value)) {
                    parentList.add(value);
                }
            }
            return;
        }

        if (annotationValue instanceof Set<?> childAnnotationValues && parentAnnotationValue instanceof Set<?> parentAnnotationValues) {
            Set<Object> parentSet = (Set<Object>) parentAnnotationValues;
            parentSet.addAll(childAnnotationValues);
        }
    }
}

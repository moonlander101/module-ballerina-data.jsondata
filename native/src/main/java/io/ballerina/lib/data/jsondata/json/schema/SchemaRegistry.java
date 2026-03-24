// Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

import java.net.URI;
import java.security.Key;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class SchemaRegistry {
    private final Map<URI, Object> schemas = new HashMap<>();
    private final Set<URI> dynamicAnchorUris = new HashSet<>();

    public void put(URI uri, Object schema) {
        this.schemas.put(uri, schema);
    }

    public Object get(URI uri) {
        return this.schemas.get(uri);
    }

    public boolean containsKey(URI uri) {
        return this.schemas.containsKey(uri);
    }

    public void registerDynamicAnchor(URI uri) {
        dynamicAnchorUris.add(uri);
    }

    public boolean isDynamicAnchor(URI uri) {
        return dynamicAnchorUris.contains(uri);
    }

    public Object resolve(URI refUri) {
        Object direct = schemas.get(refUri);
        if (direct != null) {
            return direct;
        }

        String refStr = refUri.toString();
        URI longestPrefixKey = null;
        int longestLen = -1;

        for (URI key : schemas.keySet()) {
            String keyStr = key.toString();
            if (refStr.startsWith(keyStr) && keyStr.length() > longestLen) {
                longestPrefixKey = key;
                longestLen = keyStr.length();
            }
        }

        if (longestPrefixKey == null) {
            return null;
        }

        Object root = schemas.get(longestPrefixKey);
        String remaining = refStr.substring(longestLen);

        return traversePath(root, remaining);
    }

    private Object traversePath(Object node, String remaining) {
        if (remaining.startsWith("#")) {
            remaining = remaining.substring(1);
        }

        String[] segments = remaining.split("/", -1);

        Object current = node;
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            current = step(current, segment);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    private Object step(Object current, String segment) {
        if (current instanceof Schema schema) {
            Keyword kw = schema.getKeyword(segment);
            if (kw == null) {
                return null;
            }
            return kw.getKeywordValue();
        }

        if (current instanceof Map<?, ?> map) {
            return ((Map<String, Object>) map).get(segment);
        }

        if (current instanceof List<?> list) {
            try {
                int idx = Integer.parseInt(segment);
                if (idx < 0 || idx >= list.size()) {
                    return null;
                }
                return list.get(idx);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    public Object findRootSchema() {
        HashMap<URI, Boolean> isRoot = new HashMap<>();
        for (URI uri : schemas.keySet()) {
            isRoot.put(uri, true);
        }
        for (Object schema : schemas.values()) {
            checkReferences(isRoot, schema);
        }

        int count = 0;
        URI rootUri = null;
        for (URI uri : isRoot.keySet()) {
            if (isRoot.get(uri)) {
                count += 1;
                rootUri = uri;
            }
        }
        if (count != 1) {
            throw new IllegalStateException("Expected exactly one root schema, found " + count);
        }
        return schemas.get(rootUri);
    }

    private void checkReferences(HashMap<URI, Boolean> isRoot, Object schema) {
        if (schema instanceof Schema) {
            Keyword refKeyword = ((Schema) schema).getKeyword("$ref");
            Keyword dynamicRefKeyword = ((Schema) schema).getKeyword("$dynamicRef");
            if (refKeyword != null) {
                Object refValue = refKeyword.getKeywordValue();
                if (refValue instanceof URI refURI) {
                    isRoot.put(refURI, false);
                }
            }
            if (dynamicRefKeyword != null) {
                Object refValue = dynamicRefKeyword.getKeywordValue();
                if (refValue instanceof URI refURI) {
                    isRoot.put(refURI, false);
                }
            }

            for (Keyword kw : ((Schema) schema).getKeywords().values()) {
                Object keywordValue = kw.getKeywordValue();
                if (keywordValue instanceof Schema) {
                    checkReferences(isRoot, keywordValue);
                } else if (keywordValue instanceof List<?>) {
                    for (Object item : (List<?>) keywordValue) {
                        if (item instanceof Schema) {
                            checkReferences(isRoot, item);
                        }
                    }
                } else if (keywordValue instanceof Map<?, ?>) {
                    for (Object value : ((Map<?, ?>) keywordValue).values()) {
                        if (value instanceof Schema) {
                            checkReferences(isRoot, value);
                        }
                    }
                }
            }
        }
    }

    public static boolean isAbsoluteUri(String uriString) {
        try {
            URI uri = new URI(uriString);
            return uri.isAbsolute();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isAbsoluteUri(URI uri) {
        return uri != null && uri.isAbsolute();
    }

    public static String resolveURI(String baseUri, String uriToResolve) {
        try {
            URI base = new URI(baseUri);
            URI uri = new URI(uriToResolve);

            if (uri.isAbsolute()) {
                return uri.toString();
            }

            URI resolved = base.resolve(uri);
            return resolved.toString();
        } catch (Exception e) {
            return uriToResolve;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SchemaRegistry{\n");
        sb.append("  schemas: ").append(schemas.size()).append(" entries\n");
        for (Map.Entry<URI, Object> entry : schemas.entrySet()) {
            sb.append("    ").append(entry.getKey()).append(" -> ");
            Object value = entry.getValue();
            if (value instanceof Schema) {
                Schema schema = (Schema) value;
                sb.append("Schema[").append(schema.getKeywords().keySet()).append("]");
            } else {
                sb.append(value.getClass().getSimpleName());
            }
            sb.append("\n");
        }
        if (!dynamicAnchorUris.isEmpty()) {
            sb.append("  dynamicAnchors: ").append(dynamicAnchorUris.size()).append(" entries\n");
            for (URI uri : dynamicAnchorUris) {
                sb.append("    ").append(uri).append("\n");
            }
        }
        sb.append("}");
        return sb.toString();
    }
}

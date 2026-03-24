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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class Schema {
    private static final ThreadLocal<Map<Schema, Integer>> visitedSchemas = ThreadLocal.withInitial(HashMap::new);
    private LinkedHashMap<String, Keyword> keywords;

    public Schema() {}

    public Schema(LinkedHashMap<String, Keyword> keywords) {
        this.keywords = keywords;
    }

    public Keyword getKeyword(String keywordName) {
        return keywords.get(keywordName);
    }

    public void setKeywords(LinkedHashMap<String, Keyword> keywords) {
        this.keywords = keywords;
    }

    public LinkedHashMap<String, Keyword> getKeywords() {
        return keywords;
    }

    @Override
    public String toString() {
        Map<Schema, Integer> visited = visitedSchemas.get();
        int visitCount = visited.getOrDefault(this, 0);
        if (visitCount >= 2) {
            return "[Recursive reference]";
        }

        visited.put(this, visitCount + 1);
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, Keyword> entry : keywords.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                String keywordName = entry.getKey();
                Object keywordValue = entry.getValue().getKeywordValue();
                sb.append("\"").append(keywordName).append("\": ");
                sb.append(formatValue(keywordValue));
            }
            sb.append("}");
            return sb.toString();
        } finally {
            visited.put(this, visitCount);
            if (visitCount == 0) {
                visited.remove(this);
            }
        }
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "null";
        }

        if (value instanceof Schema schema) {
            return schema.toString();
        }

        if (value instanceof Iterable<?> collection) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : collection) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append(formatValue(item));
            }
            sb.append("]");
            return sb.toString();
        }

        if (value instanceof Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                first = false;
                sb.append("\"").append(entry.getKey()).append("\": ");
                sb.append(formatValue(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }

        if (value instanceof String str) {
            return "\"" + str + "\"";
        }

        if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        }

        if (value instanceof Number num) {
            return num.toString();
        }

        return value.toString();
    }
}
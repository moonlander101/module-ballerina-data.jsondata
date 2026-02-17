package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

import java.util.HashMap;
import java.util.Map;

public class Schema {
    private static final ThreadLocal<Map<Schema, Integer>> visitedSchemas = ThreadLocal.withInitial(HashMap::new);
    private HashMap<String, Keyword> keywords;

    public Schema() {}

    public Schema(HashMap<String, Keyword> keywords) {
        this.keywords = keywords;
    }

    public Keyword getKeyword(String keywordName) {
        return keywords.get(keywordName);
    }

    public void setKeywords(HashMap<String, Keyword> keywords) {
        this.keywords = keywords;
    }

    public HashMap<String, Keyword> getKeywords() {
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
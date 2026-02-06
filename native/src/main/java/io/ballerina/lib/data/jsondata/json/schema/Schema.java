package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

import java.util.HashMap;
import java.util.Map;

public class Schema {
    private HashMap<String, Keyword> keywords;

    public Schema(HashMap<String, Keyword> keywords) {
        this.keywords = keywords;
    }

    public Keyword getKeyword(String keywordName) {
        return keywords.get(keywordName);
    }

    public HashMap<String, Keyword> getKeywords() {
        return keywords;
    }

    @Override
    public String toString() {
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
            if (keywordValue instanceof String) {
                sb.append("\"").append(keywordValue).append("\"");
            } else {
                sb.append(keywordValue);
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
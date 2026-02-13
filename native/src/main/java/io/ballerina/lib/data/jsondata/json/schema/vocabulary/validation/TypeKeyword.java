package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

public class TypeKeyword extends Keyword {
    public static final String keywordName = "type";
    public final Set<String> keywordValue;

    @Override
    public boolean evaluate(Object instance) {
        for (String keyword : keywordValue) {
            if (Objects.equals(keyword, "null") && instance == null) {
                return true;
            }
            if (Objects.equals(keyword, "string") && instance instanceof BString) {
                return true;
            }
    
            if (Objects.equals(keyword, "integer") && instance instanceof Long) {
                return true;
            }
    
            if (Objects.equals(keyword, "number") && (instance instanceof Double || instance instanceof Long)) {
                return true;
            }
    
            if (Objects.equals(keyword, "boolean") && instance instanceof Boolean) {
                return true;
            }
    
            if (Objects.equals(keyword, "object") && instance instanceof BMap<?,?>) {
                return true;
            }

            if (Objects.equals(keyword, "array") && instance instanceof BArray) {
                return true;
            }
        }
        return false;
    }

    public TypeKeyword(Set<String> typeName) {
        this.keywordValue = typeName;
    }

    public TypeKeyword(String typeName) {
        this.keywordValue = new HashSet<>();
        this.keywordValue.add(typeName);
    }

    @Override
    public Set<String> getKeywordValue() {
        return keywordValue;
    }
}

package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import java.util.ArrayList;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

public class TypeKeyword extends Keyword {
    public static final String keywordName = "type";
    public final ArrayList<String> keywordValue;

    @Override
    public boolean evaluate(Object instance) {
        for (String keyword : keywordValue) {
            if (keyword == "null" && instance == null) {
                return true;
            }
            if (keyword == "string" && instance instanceof BString) {
                return true;
            }
    
            if (keyword == "integer" && instance instanceof Long) {
                return true;
            }
    
            if (keyword == "number" && (instance instanceof Double || instance instanceof Long)) {
                return true;
            }
    
            if (keyword == "boolean" && instance instanceof Boolean) {
                return true;
            }
    
            if (keyword == "object" && instance instanceof BMap<?,?>) {
                return true;
            }

            if (keyword == "array" && instance instanceof BArray) {
                return true;
            }
        }
        return false;
    }

    public TypeKeyword(ArrayList<String> typeName) {
        this.keywordValue = typeName;
    }

    public TypeKeyword(String typeName) {
        this.keywordValue = new ArrayList<>();
        this.keywordValue.add(typeName);
    }

    @Override
    public ArrayList<String> getKeywordValue() {
        return keywordValue;
    }
}

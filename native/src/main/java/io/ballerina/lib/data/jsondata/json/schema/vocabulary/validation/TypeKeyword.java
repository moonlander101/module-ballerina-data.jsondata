package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

public class TypeKeyword extends Keyword {
    public static final String keywordName = "type";
    public final Object keywordValue;

    @Override
    public boolean evaluate(Object instance) {
        if (keywordValue == "null" && instance == null) {
            return true;
        }
        if (keywordValue == "string" && instance instanceof BString) {
            return true;
        }

        if (keywordValue == "integer" && instance instanceof Long) {
            return true;
        }

        if (keywordValue == "number" && (instance instanceof Double || instance instanceof Long)) {
            return true;
        }

        if (keywordValue == "boolean" && instance instanceof Boolean) {
            return true;
        }

        return keywordValue == "object" && instance instanceof BMap<?,?>;
    }

    public TypeKeyword(Object typeName) {
        this.keywordValue = typeName;
    }

    @Override
    public Object getKeywordValue() {
        return keywordValue;
    }
}

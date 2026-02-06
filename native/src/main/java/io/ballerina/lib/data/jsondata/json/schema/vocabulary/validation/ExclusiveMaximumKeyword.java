package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

public class ExclusiveMaximumKeyword extends Keyword {
    public static final String keywordName = "exclusiveMaximum";
    private final Double keywordValue;

    @Override
    public boolean evaluate(Object instance) {
        if (instance instanceof Double) {
            return (Double) instance < keywordValue;
        } else if (instance instanceof Long) {
            return (Long) instance < keywordValue;
        }
        return false;
    }

    public ExclusiveMaximumKeyword(Double keywordValue) {
        this.keywordValue = keywordValue;
    }

    public Object getKeywordValue() {
        return keywordValue;
    }
}

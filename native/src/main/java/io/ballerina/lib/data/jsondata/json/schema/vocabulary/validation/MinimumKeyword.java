package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

public class MinimumKeyword extends Keyword {
    public static final String keywordName = "minimum";
    private final Double keywordValue;

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        boolean valid;
        if (instance instanceof Double) {
            valid = (Double) instance >= keywordValue;
        } else if (instance instanceof Long) {
            valid = (Long) instance >= keywordValue;
        } else {
            return false;
        }
        if (!valid) {
            context.addError("minimum", "At " + context.getInstanceLocation() + ": [minimum] value " + instance + " is less than minimum " + keywordValue);
        }
        return valid;
    }

    public MinimumKeyword(Double keywordValue) {
        this.keywordValue = keywordValue;
    }

    public Object getKeywordValue() {
        return keywordValue;
    }
}

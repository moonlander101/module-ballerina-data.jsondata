package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

public class ExclusiveMaximumKeyword extends Keyword {
    public static final String keywordName = "exclusiveMaximum";
    private final Double keywordValue;

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        boolean valid;
        if (instance instanceof Double) {
            valid = (Double) instance < keywordValue;
        } else if (instance instanceof Long) {
            valid = (Long) instance < keywordValue;
        } else {
            return false;
        }
        if (!valid) {
            context.addError("exclusiveMaximum", "At " + context.getInstanceLocation() + ": [exclusiveMaximum] value " + instance + " must be less than " + keywordValue);
        }
        return valid;
    }

    public ExclusiveMaximumKeyword(Double keywordValue) {
        this.keywordValue = keywordValue;
    }

    public Object getKeywordValue() {
        return keywordValue;
    }
}

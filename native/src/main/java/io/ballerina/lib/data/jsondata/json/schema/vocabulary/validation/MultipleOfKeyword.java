package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

public class MultipleOfKeyword extends Keyword {
    public static final String keywordName = "multipleOf";
    private final Double keywordValue;

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        boolean valid;
        if (instance instanceof Double) {
            double instanceValue = (Double) instance;
            double divisor = keywordValue;
            double remainder = instanceValue % divisor;
            valid = Math.abs(remainder) < 1e-10 || Math.abs(remainder - divisor) < 1e-10;
        } else if (instance instanceof Long) {
            long instanceValue = (Long) instance;
            long divisor = keywordValue.longValue();
            valid = instanceValue % divisor == 0;
        } else {
            return false;
        }
        if (!valid) {
            context.addError("multipleOf", "At " + context.getInstanceLocation() + ": [multipleOf] value " + instance + " is not a multiple of " + keywordValue);
        }
        return valid;
    }

    public MultipleOfKeyword(Double keywordValue) {
        this.keywordValue = keywordValue;
    }

    public Object getKeywordValue() {
        return keywordValue;
    }
}

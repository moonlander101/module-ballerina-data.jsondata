package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

public class MultipleOfKeyword extends Keyword {
    public static final String keywordName = "multipleOf";
    private final Double keywordValue;

    @Override
    public boolean evaluate(Object instance) {
        if (instance instanceof Double) {
            double instanceValue = (Double) instance;
            double divisor = keywordValue;
            double remainder = instanceValue % divisor;
            return Math.abs(remainder) < 1e-10 || Math.abs(remainder - divisor) < 1e-10;
        } else if (instance instanceof Long) {
            long instanceValue = (Long) instance;
            long divisor = keywordValue.longValue();
            return instanceValue % divisor == 0;
        }
        return false;
    }

    public MultipleOfKeyword(Double keywordValue) {
        this.keywordValue = keywordValue;
    }

    public Object getKeywordValue() {
        return keywordValue;
    }
}

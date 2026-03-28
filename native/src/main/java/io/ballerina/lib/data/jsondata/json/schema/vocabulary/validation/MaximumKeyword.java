package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BDecimal;

import java.math.BigDecimal;

public class MaximumKeyword extends Keyword {
    public static final String keywordName = "maximum";
    private final Double keywordValue;

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        boolean valid = true;
        if (instance instanceof Double) {
            valid = (Double) instance <= keywordValue;
        } else if (instance instanceof Long) {
            valid = (Long) instance <= keywordValue;
        } else if (instance instanceof BDecimal) {
            valid = ((BDecimal) instance).decimalValue().compareTo(BigDecimal.valueOf(keywordValue)) <= 0;
        }

        if (!valid) {
            context.addError("maximum", "At " + context.getInstanceLocation() + ": [maximum] value " + instance + " exceeds maximum " + keywordValue);
        }
        return valid;
    }

    public MaximumKeyword(Double keywordValue) {
        this.keywordValue = keywordValue;
    }

    public Object getKeywordValue() {
        return keywordValue;
    }
}

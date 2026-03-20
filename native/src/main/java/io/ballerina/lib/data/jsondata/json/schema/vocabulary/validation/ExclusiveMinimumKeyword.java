package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BDecimal;

public class ExclusiveMinimumKeyword extends Keyword {
    public static final String keywordName = "exclusiveMinimum";
    private final Double keywordValue;

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        boolean valid = true;
        if (instance instanceof Double) {
            valid = (Double) instance > keywordValue;
        } else if (instance instanceof Long) {
            valid = (Long) instance > keywordValue;
        } else if (instance instanceof BDecimal){
            valid = ((BDecimal) instance).decimalValue().compareTo(new java.math.BigDecimal(keywordValue)) > 0;
        }
        if (!valid) {
            context.addError("exclusiveMinimum", "At " + context.getInstanceLocation() + ": [exclusiveMinimum] value " + instance + " must be greater than " + keywordValue);
        }
        return valid;
    }

    public ExclusiveMinimumKeyword(Double keywordValue) {
        this.keywordValue = keywordValue;
    }

    public Object getKeywordValue() {
        return keywordValue;
    }
}

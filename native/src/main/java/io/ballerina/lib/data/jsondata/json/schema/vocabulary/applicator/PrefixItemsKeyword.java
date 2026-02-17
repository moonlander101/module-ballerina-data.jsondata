package io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BArray;

import java.util.List;

public class PrefixItemsKeyword extends Keyword {
    public static final String keywordName = "prefixItems";
    private final List<Object> keywordValue;

    public PrefixItemsKeyword(List<Object> keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        if (!(instance instanceof BArray array)) {
            return true;
        }
        Validator validator = new Validator(false);
        long size = Math.min(array.size(), keywordValue.size());
        boolean isValid = true;
        for (long i = 0; i < size; i++) {
            EvaluationContext itemContext = context.createChildContext(String.valueOf(i), "prefixItems/" + i);
            if (!validator.validate(array.get(i), keywordValue.get((int) i), itemContext)) {
                isValid = false;
            }
        }
        context.setAnnotation("prefixItems", (long) keywordValue.size());
        return isValid;
    }

    @Override
    public Object getKeywordValue() {
        return keywordValue;
    }
}

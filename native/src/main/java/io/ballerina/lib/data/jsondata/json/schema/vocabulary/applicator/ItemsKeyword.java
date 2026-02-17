package io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BArray;

public class ItemsKeyword extends Keyword {
    public static final String keywordName = "items";
    private final Object keywordValue;

    public ItemsKeyword(Object keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        if (!(instance instanceof BArray array)) {
            return true;
        }

        long startIndex = 0;
        Object prefixAnnotation = context.getAnnotation("prefixItems");
        if (prefixAnnotation instanceof Long prefixCount) {
            startIndex = prefixCount;
        }

        Validator validator = new Validator(false);
        boolean isValid = true;
        for (long i = startIndex; i < array.size(); i++) {
            EvaluationContext itemContext = context.createChildContext(String.valueOf(i), "items");
            if (!validator.validate(array.get(i), keywordValue, itemContext)) {
                isValid = false;
            }
        }

        context.setAnnotation("items", true);
        return isValid;
    }

    @Override
    public Object getKeywordValue() {
        return keywordValue;
    }
}

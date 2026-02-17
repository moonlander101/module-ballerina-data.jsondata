package io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

import java.util.List;

public class AnyOfKeyword extends Keyword {
    public static final String keywordName = "anyOf";
    private final List<Object> keywordValue;

    public AnyOfKeyword(List<Object> keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        Validator validator = new Validator(false);
        boolean isValid = false;
        for (int i = 0; i < keywordValue.size(); i++) {
            EvaluationContext schemaContext = context.createChildContext("", "anyOf/" + i);

            if (validator.validate(instance, keywordValue.get(i), schemaContext)) {
                isValid = true;
            }
        }
        context.addError("anyOf", "At " + context.getInstanceLocation() + ": [anyOf] value does not match any of the subschemas");
        return isValid;
    }

    @Override
    public Object getKeywordValue() {
        return keywordValue;
    }
}

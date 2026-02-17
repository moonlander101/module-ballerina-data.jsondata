package io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

import java.util.List;

public class OneOfKeyword extends Keyword {
    public static final String keywordName = "oneOf";
    private final List<Object> keywordValue;

    public OneOfKeyword(List<Object> keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
public boolean evaluate(Object instance, EvaluationContext context) {
        Validator validator = new Validator(false);
        int matchCount = 0;
        for (int i = 0; i < keywordValue.size(); i++) {
            EvaluationContext schemaContext = context.createChildContext("", "oneOf/" + i);
            if (validator.validate(instance, keywordValue.get(i), schemaContext)) {
                matchCount++;
                if (matchCount > 1) {
                    context.addError("oneOf", "At " + context.getInstanceLocation() + ": [oneOf] value matches more than one subschema");
                    return false;
                }
            }
        }
        if (matchCount == 0) {
            context.addError("oneOf", "At " + context.getInstanceLocation() + ": [oneOf] value does not match exactly one subschema");
        }
        return matchCount == 1;
    }

    @Override
    public Object getKeywordValue() {
        return keywordValue;
    }
}

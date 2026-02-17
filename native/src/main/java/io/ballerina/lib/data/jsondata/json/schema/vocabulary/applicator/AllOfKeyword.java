package io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

import java.util.List;

public class AllOfKeyword extends Keyword {
    public static final String keywordName = "allOf";
    private final List<Object> keywordValue;

    public AllOfKeyword(List<Object> keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        Validator validator = new Validator(false);
        for (int i = 0; i < keywordValue.size(); i++) {
            EvaluationContext schemaContext = context.createChildContext("", "allOf/" + i);
            if (!validator.validate(instance, keywordValue.get(i), schemaContext)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Object getKeywordValue() {
        return keywordValue;
    }
}

package io.ballerina.lib.data.jsondata.json.schema.vocabulary.core;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

public class IdKeyword extends Keyword {
    public static final String keywordName = "$id";
    private final Object keywordValue;


    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        return true;
    }

    public IdKeyword(Object keywordValue) {
        this.keywordValue = keywordValue;
    }

    public Object getKeywordValue() {
        return keywordValue;
    }

}

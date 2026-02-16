package io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator;

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
    public boolean evaluate(Object instance) {
        Validator validator = new Validator(true);
        for (Object schema : keywordValue) {
            if (validator.validate(instance, schema)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object getKeywordValue() {
        return keywordValue;
    }
}

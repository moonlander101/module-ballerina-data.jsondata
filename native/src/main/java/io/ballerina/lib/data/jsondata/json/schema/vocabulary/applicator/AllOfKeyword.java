package io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator;

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
    public boolean evaluate(Object instance) {
        Validator validator = new Validator(true);
        for (Object schema : keywordValue) {
            if (!validator.validate(instance, schema)) {
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

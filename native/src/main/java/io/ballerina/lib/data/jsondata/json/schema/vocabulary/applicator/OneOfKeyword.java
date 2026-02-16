package io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator;

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
    public boolean evaluate(Object instance) {
        Validator validator = new Validator(true);
        int matchCount = 0;
        for (Object schema : keywordValue) {
            if (validator.validate(instance, schema)) {
                matchCount++;
                if (matchCount > 1) {
                    return false;
                }
            }
        }
        return matchCount == 1;
    }

    @Override
    public Object getKeywordValue() {
        return keywordValue;
    }
}

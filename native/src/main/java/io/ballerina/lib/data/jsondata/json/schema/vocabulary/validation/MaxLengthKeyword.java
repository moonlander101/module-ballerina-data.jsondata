package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BString;

public class MaxLengthKeyword extends Keyword {
    public static final String keywordName = "maxLength";
    private final Long keywordValue;

    @Override
    public boolean evaluate(Object instance) {
        if (!(instance instanceof BString str)) {
            return true;
        }
        return str.length() <= keywordValue;
    }

    public MaxLengthKeyword(Long keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public Long getKeywordValue() {
        return keywordValue;
    }
}

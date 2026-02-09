package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BString;

public class MinLengthKeyword extends Keyword {
    public static final String keywordName = "minLength";
    private final Long keywordValue;

    @Override
    public boolean evaluate(Object instance) {
        if (!(instance instanceof BString str)) {
            return true;
        }
        return str.length() >= keywordValue;
    }

    public MinLengthKeyword(Long keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public Long getKeywordValue() {
        return keywordValue;
    }
}

package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.utils.JsonEqualityUtils;

public class ConstKeyword extends Keyword {
    public static final String keywordName = "const";
    private final Object keywordValue;

    @Override
    public boolean evaluate(Object instance) {
        return JsonEqualityUtils.deepEquals(keywordValue, instance);
    }

    public ConstKeyword(Object keywordValue) {
        this.keywordValue = keywordValue;
    }

    public Object getKeywordValue() {
        return keywordValue;
    }
}

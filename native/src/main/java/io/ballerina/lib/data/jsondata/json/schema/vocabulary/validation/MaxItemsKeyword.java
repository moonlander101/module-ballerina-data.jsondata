package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BArray;

public class MaxItemsKeyword extends Keyword {
    public static final String keywordName = "maxItems";
    private final Long keywordValue;

    @Override
    public boolean evaluate(Object instance) {
        if (!(instance instanceof BArray array)) {
            return true;
        }
        return array.size() <= keywordValue;
    }

    public MaxItemsKeyword(Long keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public Long getKeywordValue() {
        return keywordValue;
    }
}

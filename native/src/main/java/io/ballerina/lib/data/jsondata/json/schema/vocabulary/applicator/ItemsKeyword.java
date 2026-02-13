package io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator;

import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BArray;

public class ItemsKeyword extends Keyword {
    public static final String keywordName = "items";
    private final Object keywordValue;

    public ItemsKeyword(Object keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public boolean evaluate(Object instance) {
        if (!(instance instanceof BArray array)) {
            return true;
        }
        Validator validator = new Validator(true);
        long size = array.size();
        for (long i = 0; i < size; i++) {
            if (!validator.validate(array.get(i), keywordValue)) {
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

package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import java.util.Set;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.utils.JsonEqualityUtils;

public class EnumKeyword extends Keyword {
    public static final String keywordName = "enum";
    private final Set<Object> keywordValue;

    @Override
    public boolean evaluate(Object instance) {
        for (Object value : keywordValue) {
            if (JsonEqualityUtils.deepEquals(value, instance)) {
                return true;
            }
        }
        return false;
    }

    public EnumKeyword(Set<Object> keywordValue) {
        this.keywordValue = keywordValue;
    }

    public Set<Object> getKeywordValue() {
        return keywordValue;
    }
}

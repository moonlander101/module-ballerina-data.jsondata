package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BString;

import java.util.regex.Pattern;

public class PatternKeyword extends Keyword {
    public static final String keywordName = "pattern";
    private final String keywordValue;
    private final Pattern pattern;

    @Override
    public boolean evaluate(Object instance) {
        if (!(instance instanceof BString str)) {
            return true;
        }
        return pattern.matcher(str.getValue()).matches();
    }

    public PatternKeyword(String keywordValue) {
        this.keywordValue = keywordValue;
        this.pattern = Pattern.compile(keywordValue);
    }

    @Override
    public String getKeywordValue() {
        return keywordValue;
    }
}

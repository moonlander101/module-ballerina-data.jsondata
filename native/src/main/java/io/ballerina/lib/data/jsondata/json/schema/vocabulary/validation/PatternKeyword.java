package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BString;

import java.util.regex.Pattern;

public class PatternKeyword extends Keyword {
    public static final String keywordName = "pattern";
    private final String keywordValue;
    private final Pattern pattern;

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        if (!(instance instanceof BString str)) {
            return true;
        }
        boolean valid = pattern.matcher(str.getValue()).matches();
        if (!valid) {
            context.addError("pattern", "At " + context.getInstanceLocation() + ": [pattern] value does not match pattern " + keywordValue);
        }
        return valid;
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

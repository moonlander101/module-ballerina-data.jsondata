package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.*;

public class Validator {
    private final boolean failFast;

    public Validator(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean validate(Object instance, Schema schema) {
        boolean isValid = true;

        for (String key : schema.getKeywords().keySet()) {
            Keyword keyword = schema.getKeyword(key);
            if (keyword != null) {
                isValid = isValid && keyword.evaluate(instance);
            }

            if (!isValid && failFast) {
                return false;
            }
        }

        return isValid;
    }
}

package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.*;

public class Validator {
    private final boolean failFast;

    public Validator(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean validate(Object instance, Object schema) {
        boolean isValid = true;
        System.out.println("Schema: " + schema);
        if (schema instanceof Boolean) {
            return (boolean) schema;
        }
        for (String key : ((Schema) schema).getKeywords().keySet()) {
            Keyword keyword = ((Schema) schema).getKeyword(key);
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

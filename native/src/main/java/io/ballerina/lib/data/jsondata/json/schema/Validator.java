package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.*;

public class Validator {
    private final boolean failFast;

    public Validator(boolean failFast) {
        this.failFast = failFast;
    }

    public boolean validate(Object instance, Object schema, EvaluationContext context) {
        if (schema instanceof Boolean) {
            if (!(boolean) schema) {
                context.addError("schema", "At " + context.getInstanceLocation() + ": value is not allowed (false schema)");
            }
            return (boolean) schema;
        }

        boolean isValid = true;
        for (String key : ((Schema) schema).getKeywords().keySet()) {
            Keyword keyword = ((Schema) schema).getKeyword(key);
            if (keyword != null) {
                isValid = isValid && keyword.evaluate(instance, context);
            }

            if (!isValid && failFast) {
                return false;
            }
        }

        return isValid;
    }
}

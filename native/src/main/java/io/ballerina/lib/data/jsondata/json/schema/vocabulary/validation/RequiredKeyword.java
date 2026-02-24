package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.ArrayList;

public class RequiredKeyword extends Keyword {
    public static final String keywordName = "required";
    public final ArrayList<String> keywordValue;

    public RequiredKeyword(ArrayList<String> keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public Object getKeywordValue() {
        return keywordValue;
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        if (!(instance instanceof BMap<?, ?> bMap)) {
            return true;
        }

        boolean isValid = true;
        ArrayList<String> missingProperties = new ArrayList<>();

        for (String requiredProperty : keywordValue) {
            BString propertyKey = StringUtils.fromString(requiredProperty);
            if (!bMap.containsKey(propertyKey)) {
                missingProperties.add(requiredProperty);
                isValid = false;
            }
        }

        if (!isValid) {
            String missingList = String.join(", ", missingProperties);
            context.addError("required",
                "At " + context.getInstanceLocation() + ": [required] missing required properties: " + missingList);
        }

        return isValid;
    }
}
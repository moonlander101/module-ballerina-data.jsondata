package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BArray;

import java.util.ArrayList;
import java.util.List;

public class ContainsKeyword extends Keyword {
    public static final String keywordName = "contains";
    private final Long minContains;
    private final Long maxContains;
    private final Object containsSchema;

    public ContainsKeyword(Long minContains, Long maxContains, Object containsSchema) {
        this.minContains = minContains != null ? minContains : 0L;
        this.maxContains = maxContains;
        this.containsSchema = containsSchema;
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        if (!(instance instanceof BArray array)) {
            return true;
        }

        Validator validator = new Validator(false);
        List<Long> matchingIndices = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            EvaluationContext itemContext = context.createChildContext(String.valueOf(i), "contains/" + i);
            if (validator.validate(array.get(i), containsSchema, itemContext)) {
                matchingIndices.add((long) i);
            }
        }

        long matchCount = matchingIndices.size();

        boolean valid = matchCount >= minContains;
        if (maxContains != null) {
            valid = valid && matchCount <= maxContains;
        }

        if (!valid) {
            if (maxContains != null) {
                context.addError("contains", "At " + context.getInstanceLocation() + 
                    ": [contains] expected " + minContains + " to " + maxContains + 
                    " matches, found " + matchCount);
            } else {
                context.addError("contains", "At " + context.getInstanceLocation() + 
                    ": [contains] expected at least " + minContains + 
                    " matches, found " + matchCount);
            }
        }

        if (matchCount == array.size() && !array.isEmpty()) {
            context.setAnnotation("contains", true);
        } else {
            context.setAnnotation("contains", matchingIndices);
        }
        return valid;
    }

    @Override
    public Object getKeywordValue() {
        return containsSchema;
    }

    public Long getMinContains() {
        return minContains;
    }

    public Long getMaxContains() {
        return maxContains;
    }
}

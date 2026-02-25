package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.IncrementalKeyword;
import io.ballerina.lib.data.jsondata.utils.JsonEqualityUtils;
import io.ballerina.runtime.api.values.BArray;

import java.util.ArrayList;
import java.util.List;

public class UniqueItemsKeyword extends Keyword implements IncrementalKeyword {
    public static final String keywordName = "uniqueItems";
    private final Boolean keywordValue;
    
    // Incremental state
    private List<Object> seenItems;
    private boolean isValid;
    private int currentIndex;

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        if (!(instance instanceof BArray array)) {
            return true;
        }
        if (!keywordValue) {
            return true;
        }
        int size = array.size();
        for (int i = 0; i < size; i++) {
            for (int j = i + 1; j < size; j++) {
                if (JsonEqualityUtils.deepEquals(array.get(i), array.get(j))) {
                    context.addError("uniqueItems", "At " + context.getInstanceLocation() + ": [uniqueItems] array contains duplicate items at indices " + i + " and " + j);
                    return false;
                }
            }
        }
        return true;
    }

    public UniqueItemsKeyword(Boolean keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public Boolean getKeywordValue() {
        return keywordValue;
    }
    
    // Incremental protocol implementation
    
    @Override
    public void begin(Object container, EvaluationContext context) {
        this.seenItems = new ArrayList<>();
        this.isValid = true;
        this.currentIndex = 0;
    }
    
    @Override
    public boolean acceptElement(String key, Object value, int index, EvaluationContext context) {
        if (!keywordValue) {
            return true; // uniqueItems is false, no validation needed
        }
        
        // Compare against all previously seen items
        for (int i = 0; i < seenItems.size(); i++) {
            if (JsonEqualityUtils.deepEquals(seenItems.get(i), value)) {
                context.addError("uniqueItems", 
                    "At " + context.getInstanceLocation() + ": [uniqueItems] array contains duplicate items at indices " + 
                    i + " and " + currentIndex);
                isValid = false;
                return false; // Early exit on first duplicate
            }
        }
        
        seenItems.add(value);
        currentIndex++;
        return true; // Continue iteration
    }
    
    @Override
    public boolean finish(EvaluationContext context) {
        return isValid;
    }
}

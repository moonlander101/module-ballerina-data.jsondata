// Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.IncrementalKeyword;
import io.ballerina.runtime.api.values.BArray;

import java.util.ArrayList;
import java.util.List;

public class ContainsKeyword extends Keyword implements IncrementalKeyword {
    public static final String keywordName = "contains";
    private final Long minContains;
    private final Long maxContains;
    private final Object containsSchema;
    
    // Incremental state
    private List<Long> matchingIndices;
    private Validator validator;
    private int currentIndex;
    private long arraySize;

    public ContainsKeyword(Long minContains, Long maxContains, Object containsSchema) {
        this.minContains = minContains != null ? minContains : 1L;
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
            valid = valid && (matchCount <= maxContains);
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
    
    // Incremental protocol implementation
    
    @Override
    public void begin(Object container, EvaluationContext context) {
        this.matchingIndices = new ArrayList<>();
        this.validator = new Validator(false);
        this.currentIndex = 0;
        if (container instanceof BArray array) {
            this.arraySize = array.size();
        } else {
            this.arraySize = 0;
        }
    }
    
    @Override
    public boolean acceptElement(String key, Object value, int index, EvaluationContext context) {
        EvaluationContext itemContext = context.createChildContext(
            String.valueOf(currentIndex), "contains/" + currentIndex);
        
        if (validator.validate(value, containsSchema, itemContext)) {
            matchingIndices.add((long) currentIndex);
        }
        
        currentIndex++;
        return true; // Continue iteration
    }
    
    @Override
    public boolean finish(EvaluationContext context) {
        long matchCount = matchingIndices.size();
        
        boolean valid = matchCount >= minContains;
        if (maxContains != null) {
            valid = valid && (matchCount <= maxContains);
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
        
        if (matchCount == arraySize && arraySize > 0) {
            context.setAnnotation("contains", true);
        } else {
            context.setAnnotation("contains", matchingIndices);
        }
        
        return valid;
    }
}

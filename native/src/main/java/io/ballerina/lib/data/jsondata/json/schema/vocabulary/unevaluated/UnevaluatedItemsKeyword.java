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

package io.ballerina.lib.data.jsondata.json.schema.vocabulary.unevaluated;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Schema;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BArray;

import java.util.HashSet;
import java.util.List;

public class UnevaluatedItemsKeyword extends Keyword {
    public static final String keywordName = "unevaluatedItems";
    private final Object keywordValue;

    public UnevaluatedItemsKeyword(Object keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public Object getKeywordValue() {
        return this.keywordValue;
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        boolean isValid = true;
        if (!(instance instanceof BArray array)) {
            return isValid;
        }

        Object itemsAnnotation = context.getAnnotation("items");
        if (itemsAnnotation instanceof Boolean itemsBool && itemsBool) {
            return isValid;
        }

        Object evaluatedItemsAnnotation = context.getAnnotation("evaluatedItems");
        if (evaluatedItemsAnnotation instanceof Boolean evaluatedItemsBool && evaluatedItemsBool) {
            return isValid;
        }

        Object containsAnnotation = context.getAnnotation("contains");
        if (containsAnnotation instanceof Boolean containsBool && containsBool) {
            return isValid;
        }

        long prefixEndIndex = -1;
        Object prefixItemsAnnotation = context.getAnnotation("prefixItems");
        if (prefixItemsAnnotation instanceof Boolean prefixItemsBool && prefixItemsBool) {
            return isValid;
        } else if (prefixItemsAnnotation instanceof Long largestIndex) {
            prefixEndIndex = largestIndex;
            if (prefixEndIndex + 1 >= array.size()) {
                return isValid;
            }
        }

        HashSet<Long> evaluatedIndices = new HashSet<>();
        if (evaluatedItemsAnnotation instanceof List<?> evaluatedItems) {
            for (Object idx : evaluatedItems) {
                if (idx instanceof Long i) {
                    evaluatedIndices.add(i);
                } else if (idx instanceof Integer l) {
                    evaluatedIndices.add(Long.valueOf(l));
                }
            }
        }

        if (containsAnnotation instanceof List<?> containsIndices) {
            for (Object idx : containsIndices) {
                if (idx instanceof Long i) {
                    evaluatedIndices.add(i);
                } else if (idx instanceof Integer l) {
                    evaluatedIndices.add(Long.valueOf(l));
                }
            }
        }

        Validator validator = new Validator(false);
        long startIndex = prefixEndIndex + 1;
        for (long i = startIndex; i < array.size(); i++) {
            if (!evaluatedIndices.contains(i)) {
                Object item = array.get(i);
                EvaluationContext itemContext = context.createChildContext(String.valueOf(i), "unevaluatedItems");
                if (!validator.validate(item, keywordValue, itemContext)) {
                    context.addError("unevaluatedItems", "At " + context.getInstanceLocation() + "/" + i +
                            ": [unevaluatedItems] item at index " + i + " is not valid against the unevaluatedItems schema");
                    isValid = false;
                }
            }
        }

        if (isValid) {
            context.setAnnotation("evaluatedItems", true);
        }
        return isValid;
    }
}

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

package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.IdKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.*;
import io.ballerina.lib.data.jsondata.utils.SchemaValidatorUtils;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Validator {
    private final boolean failFast;

    private static final List<String> KEYWORD_ORDER = List.of(
            "properties",
            "patternProperties",
            "additionalProperties",
            "propertyNames",
            "prefixItems",
            "items",
            "$ref",
            "$dynamicRef",
            "dependentSchemas",
            "if",
            "then",
            "else",
            "contains",
            "anyOf",
            "allOf",
            "oneOf",
            "unevaluatedProperties",
            "unevaluatedItems"
    );

    public Validator(boolean failFast) {
        this.failFast = failFast;
    }

    private List<String> getOrderedKeys(LinkedHashMap<String, Keyword> keywords) {
        List<String> result = new ArrayList<>();
        Set<String> remainingKeywords = new LinkedHashSet<>(keywords.keySet());

        for (String orderedKeyword : KEYWORD_ORDER) {
            if (remainingKeywords.contains(orderedKeyword)) {
                result.add(orderedKeyword);
                remainingKeywords.remove(orderedKeyword);
            }
        }

        result.addAll(remainingKeywords);

        return result;
    }

    public boolean validate(Object instance, Object schema, EvaluationContext context) {
        if (schema instanceof Boolean boolSchema) {
            if (!boolSchema) {
                context.addError("schema", "At " + context.getInstanceLocation() + ": value is not allowed (false schema)");
            }
            return boolSchema;
        }

        boolean isValid = true;
        boolean pushedScope = false;

        List<String> orderedKeys = getOrderedKeys(((Schema) schema).getKeywords());
        Keyword idKeyword = ((Schema) schema).getKeyword(IdKeyword.keywordName);
        if (idKeyword != null) {
            Object idValue = idKeyword.getKeywordValue();
            if (idValue instanceof URI) {
                System.out.println("Pushing dynamic scope for $id: " + idValue);
                context.pushDynamicScope((URI) idValue);
                pushedScope = true;
            }
        }

        for (String key : orderedKeys) {
//            System.out.println("Evaluating keyword: " + key);
            Keyword keyword = ((Schema) schema).getKeyword(key);
            if (keyword != null) {
                boolean keywordValid = keyword.evaluate(instance, context);
                isValid = isValid && keywordValid;
            }

            if (!isValid && failFast) {
                return false;
            }
        }
        if (pushedScope) {
            context.popDynamicScope();
        }
        return isValid;
    }
}

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

package io.ballerina.lib.data.jsondata.json.schema.vocabulary.core;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.SchemaRegistry;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

public class RefKeyword extends Keyword {

    public static final String keywordName = "$ref";

    private final URI refUri;

    private static final ThreadLocal<Set<URI>> EVALUATING_REFS =
            ThreadLocal.withInitial(HashSet::new);

    public RefKeyword(URI refUri) {
        this.refUri = refUri;
    }

    @Override
    public Object getKeywordValue() {
        return refUri.toString();
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        SchemaRegistry registry = context.getSchemaRegistry();
        if (registry == null) {
            context.addError(keywordName,
                    "At " + context.getInstanceLocation()
                            + ": schema registry is required for $ref resolution");
            return false;
        }

        // Cycle detection
        Set<URI> evaluating = EVALUATING_REFS.get();
        if (evaluating.contains(refUri)) {
            return true; // break cycle — treat as valid
        }
        evaluating.add(refUri);
        try {
            Object target = registry.resolve(refUri);
            if (target == null) {
                context.addError(keywordName,
                        "At " + context.getInstanceLocation()
                                + ": unresolved $ref '" + refUri + "'");
                return false;
            }

            EvaluationContext refContext = context.createChildContext("", keywordName);
            return new Validator(false).validate(instance, target, refContext);
        } finally {
            evaluating.remove(refUri);
        }
    }
}

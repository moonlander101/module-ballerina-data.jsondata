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

package io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.IncrementalKeyword;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

public class PropertyNamesKeyword extends Keyword implements IncrementalKeyword {
    public static final String keywordName = "propertyNames";
    private final Object schema;
    
    // Incremental state
    private boolean isValid;
    private Validator validator;

    public PropertyNamesKeyword(Object schema) {
        this.schema = schema;
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        if (!(instance instanceof BMap<?, ?> bMap)) {
            return true;
        }

        Validator validator = new Validator(false);
        boolean isValid = true;

        for (BString propertyKey : ((BMap<BString, Object>) bMap).getKeys()) {
            String propertyName = propertyKey.getValue();

            EvaluationContext propertyNameContext = context.createChildContext(
                propertyName, "propertyNames/" + propertyName);

            if (!validator.validate(propertyKey, schema, propertyNameContext)) {
                isValid = false;
            }
        }

        context.setAnnotation(keywordName, true);

        return isValid;
    }

    @Override
    public Object getKeywordValue() {
        return schema;
    }
    
    // Incremental protocol implementation
    
    @Override
    public void begin(Object container, EvaluationContext context) {
        this.isValid = true;
        this.validator = new Validator(false);
    }
    
    @Override
    public boolean acceptElement(String key, Object value, int index, EvaluationContext context) {
        if (key == null) {
            return true; // Not an object property
        }
        
        BString propertyKey = io.ballerina.runtime.api.utils.StringUtils.fromString(key);
        EvaluationContext propertyNameContext = context.createChildContext(
            key, "propertyNames/" + key);
        
        if (!validator.validate(propertyKey, schema, propertyNameContext)) {
            isValid = false;
        }
        
        return true; // Continue iteration even on failure
    }
    
    @Override
    public boolean finish(EvaluationContext context) {
        context.setAnnotation(keywordName, true);
        return isValid;
    }
}
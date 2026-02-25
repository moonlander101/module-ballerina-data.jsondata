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

import java.util.HashSet;
import java.util.Set;

public class AdditionalPropertiesKeyword extends Keyword implements IncrementalKeyword {
    public static final String keywordName = "additionalProperties";
    private final Object additionalSchema;
    
    // Incremental state
    private boolean isValid;
    private Set<String> validatedPropertyNames;
    private Validator validator;

    public AdditionalPropertiesKeyword(Object additionalSchema) {
        this.additionalSchema = additionalSchema;
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        if (!(instance instanceof BMap<?, ?> bMap)) {
            return true;
        }

        Set<String> propertiesMatched = null;
        Object propertiesAnnotation = context.getAnnotation(PropertiesKeyword.keywordName);
        if (propertiesAnnotation instanceof Set) {
            propertiesMatched = (Set<String>) propertiesAnnotation;
        }

        Set<String> patternPropertiesMatched = null;
        Object patternPropertiesAnnotation = context.getAnnotation("patternProperties");
        if (patternPropertiesAnnotation instanceof Set) {
            patternPropertiesMatched = (Set<String>) patternPropertiesAnnotation;
        }

        if (additionalSchema instanceof Boolean) {
            if (!(boolean) additionalSchema) {
                boolean isValid = true;
                for (BString key : ((BMap<BString, Object>) bMap).getKeys()) {
                    String propertyName = key.getValue();

                    boolean isDefinedProperty = propertiesMatched != null &&
                                          propertiesMatched.contains(propertyName);
                    boolean isPatternProperty = patternPropertiesMatched != null &&
                                          patternPropertiesMatched.contains(propertyName);

                    if (!isDefinedProperty && !isPatternProperty) {
                        context.addError("additionalProperties",
                            "At " + context.getInstanceLocation() + "/" + propertyName +
                            ": [additionalProperties=false] property '" + propertyName +
                            "' is not allowed");
                        isValid = false;
                    }
                }
                return isValid;
            }
            return true;
        }

        Validator validator = new Validator(false);
        boolean isValid = true;

        Set<String> validatedPropertyNames = new HashSet<>();

        for (BString key : ((BMap<BString, Object>) bMap).getKeys()) {
            String propertyName = key.getValue();

            if (propertiesMatched != null && propertiesMatched.contains(propertyName)) {
                continue;
            }
            if (patternPropertiesMatched != null && patternPropertiesMatched.contains(propertyName)) {
                continue;
            }

            EvaluationContext propertyContext = context.createChildContext(
                propertyName, "additionalProperties/" + propertyName);

            if (validator.validate(bMap.get(key), additionalSchema, propertyContext)) {
                validatedPropertyNames.add(propertyName);
            } else {
                isValid = false;
            }
        }

        context.setAnnotation(keywordName, validatedPropertyNames);

        return isValid;
    }

    @Override
    public Object getKeywordValue() {
        return additionalSchema;
    }
    
    // Incremental protocol implementation
    
    @Override
    public Phase getEvaluationPhase() {
        return Phase.DEPENDENT; // Must run after PropertiesKeyword and PatternPropertiesKeyword
    }
    
    @Override
    public void begin(Object container, EvaluationContext context) {
        this.isValid = true;
        this.validatedPropertyNames = new HashSet<>();
        this.validator = new Validator(false);
    }
    
    @Override
    public boolean acceptElement(String key, Object value, int index, EvaluationContext context) {
        if (key == null) {
            return true; // Not an object property
        }
        
        // Check if already matched by patternProperties (set incrementally by PRIMARY phase keyword)
        // No need to check propertiesMatched here because acceptElement() is only called for
        // rest-field keys (declared record fields are never passed to rest-only keywords).
        Set<String> patternPropertiesMatched = null;
        Object patternPropertiesAnnotation = context.getAnnotation(PatternPropertiesKeyword.keywordName);
        if (patternPropertiesAnnotation instanceof Set) {
            patternPropertiesMatched = (Set<String>) patternPropertiesAnnotation;
        }
        
        if (patternPropertiesMatched != null && patternPropertiesMatched.contains(key)) {
            return true;
        }
        
        // This is an additional property (not matched by properties or patternProperties)
        if (additionalSchema instanceof Boolean) {
            if (!(boolean) additionalSchema) {
                context.addError("additionalProperties",
                    "At " + context.getInstanceLocation() + "/" + key +
                    ": [additionalProperties=false] property '" + key +
                    "' is not allowed");
                isValid = false;
            }
        } else {
            // Validate against the additional schema
            EvaluationContext propertyContext = context.createChildContext(
                key, "additionalProperties/" + key);
            if (validator.validate(value, additionalSchema, propertyContext)) {
                validatedPropertyNames.add(key);
            } else {
                isValid = false;
            }
        }
        
        return true; // Continue iteration
    }
    
    @Override
    public boolean finish(EvaluationContext context) {
        context.setAnnotation(keywordName, validatedPropertyNames);
        return isValid;
    }
}

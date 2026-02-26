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
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PatternPropertiesKeyword extends Keyword implements IncrementalKeyword {
    public static final String keywordName = "patternProperties";
    private final Map<Pattern, Object> patternSchemaMap;
    
    // Incremental state
    private boolean isValid;
    private Set<String> matchedPropertyNames;
    private Validator validator;

    public PatternPropertiesKeyword(Map<String, Object> patternSchemaMap) {
        this.patternSchemaMap = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : patternSchemaMap.entrySet()) {
            String patternStr = entry.getKey();
            try {
                Pattern regex = Pattern.compile(patternStr);
                this.patternSchemaMap.put(regex, entry.getValue());
            } catch (PatternSyntaxException e) {
                throw DiagnosticLog.createJsonError("invalid regex pattern: " + patternStr);
            }
        }
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        if (!(instance instanceof BMap<?, ?> bMap)) {
            return true;
        }

        Validator validator = new Validator(false);
        boolean isValid = true;
        Set<String> matchedPropertyNames = new HashSet<>();

        for (BString propertyKey : ((BMap<BString, Object>) bMap).getKeys()) {
            String propertyName = propertyKey.getValue();
            Object propertyValue = bMap.get(propertyKey);
            boolean propertyMatched = false;

            for (Map.Entry<Pattern, Object> entry : patternSchemaMap.entrySet()) {
                Pattern pattern = entry.getKey();
                Object schema = entry.getValue();

                if (pattern.matcher(propertyName).matches()) {
                    propertyMatched = true;

                    EvaluationContext propertyContext = context.createChildContext(
                        propertyName, "patternProperties/" + pattern.pattern());

                    if (!validator.validate(propertyValue, schema, propertyContext)) {
                        isValid = false;
                    }
                }
            }

            if (propertyMatched) {
                matchedPropertyNames.add(propertyName);
            }
        }

        context.setAnnotation(keywordName, matchedPropertyNames);
        return isValid;
    }

    @Override
    public Object getKeywordValue() {
        return patternSchemaMap;
    }
    
    // Incremental protocol implementation
    
    @Override
    public Phase getEvaluationPhase() {
        return Phase.ANNOTATION_PRODUCER; // Must run before AdditionalPropertiesKeyword
    }
    
    @Override
    public void begin(Object container, EvaluationContext context) {
        this.isValid = true;
        this.matchedPropertyNames = new HashSet<>();
        this.validator = new Validator(false);
    }
    
    @Override
    public boolean acceptElement(String key, Object value, int index, EvaluationContext context) {
        if (key == null) {
            return true; // Not an object property
        }
        
        boolean propertyMatched = false;
        
        for (Map.Entry<Pattern, Object> entry : patternSchemaMap.entrySet()) {
            Pattern pattern = entry.getKey();
            Object schema = entry.getValue();
            
            if (pattern.matcher(key).matches()) {
                propertyMatched = true;
                
                EvaluationContext propertyContext = context.createChildContext(
                    key, "patternProperties/" + pattern.pattern());
                
                if (!validator.validate(value, schema, propertyContext)) {
                    isValid = false;
                }
            }
        }
        
        if (propertyMatched) {
            matchedPropertyNames.add(key);
            context.setAnnotation(keywordName, matchedPropertyNames);
        }
        
        return true; // Continue iteration
    }
    
    @Override
    public boolean finish(EvaluationContext context) {
        // Annotation already set incrementally during acceptElement
        return isValid;
    }
}
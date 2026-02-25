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

package io.ballerina.lib.data.jsondata.json.schema.vocabulary;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;

/**
 * Interface for keywords that can be evaluated incrementally during iteration over container elements.
 * This allows validation to occur inline with type conversion in JsonTraverse, avoiding double-traversal.
 * 
 * Keywords that implement this interface support both the standard evaluate() method (for standalone
 * validation) and the incremental protocol (for inline validation during parseAsType conversion).
 * 
 * Some keywords depend on annotations set by other keywords (e.g., AdditionalPropertiesKeyword depends
 * on PropertiesKeyword and PatternPropertiesKeyword). The getEvaluationPhase() method controls ordering.
 * 
 * @since 0.1.0
 */
public interface IncrementalKeyword {
    
    /**
     * Defines the evaluation phase for ordering keyword execution.
     * Lower phase numbers run before higher phase numbers.
     */
    enum Phase {
        /** Phase 0: Keywords that set annotations needed by other keywords (e.g., properties, patternProperties) */
        PRIMARY(0),
        /** Phase 1: Keywords that depend on annotations from PRIMARY phase (e.g., additionalProperties) */
        DEPENDENT(1),
        /** Phase 2: All other keywords with no dependencies */
        INDEPENDENT(2);
        
        private final int order;
        
        Phase(int order) {
            this.order = order;
        }
        
        public int getOrder() {
            return order;
        }
    }
    
    /**
     * Returns the evaluation phase for this keyword. Determines when this keyword
     * runs relative to other keywords during incremental validation.
     * 
     * @return the phase for this keyword
     */
    default Phase getEvaluationPhase() {
        return Phase.INDEPENDENT;
    }
    
    /**
     * Called once before iteration begins. Receives the container-level instance.
     * Use this to initialize any state needed for incremental validation.
     * 
     * @param container The full container instance (BMap for objects, BArray for arrays)
     * @param context The evaluation context for error reporting and annotations
     */
    void begin(Object container, EvaluationContext context);
    
    /**
     * Called once per element during iteration.
     * 
     * For objects: key=propertyName, value=propertyValue, index=-1
     * For arrays:  key=null, value=element, index=arrayIndex
     * 
     * @param key The property name (for objects) or null (for arrays)
     * @param value The element value
     * @param index The array index (for arrays) or -1 (for objects)
     * @param context The evaluation context for error reporting and annotations
     * @return true to continue iteration, false to stop early (fail-fast)
     */
    boolean acceptElement(String key, Object value, int index, EvaluationContext context);
    
    /**
     * Called once after iteration completes. Returns the final validation verdict.
     * Use this to perform any finalization checks and return the accumulated result.
     * 
     * @param context The evaluation context for error reporting and annotations
     * @return true if validation passed, false otherwise
     */
    boolean finish(EvaluationContext context);
}

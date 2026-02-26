/*
 * Copyright (c) 2024, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.data.jsondata.json;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Schema;
import io.ballerina.lib.data.jsondata.json.schema.TypeParser;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.IncrementalKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PropertyNamesKeyword;
import io.ballerina.lib.data.jsondata.utils.Constants;
import io.ballerina.lib.data.jsondata.utils.DataUtils;
import io.ballerina.lib.data.jsondata.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.IntersectionType;
import io.ballerina.runtime.api.types.MapType;
import io.ballerina.runtime.api.types.PredefinedTypes;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.TupleType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.utils.ValueUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import static io.ballerina.lib.data.jsondata.utils.Constants.ENABLE_CONSTRAINT_VALIDATION;

/**
 * Traverse json tree.
 *
 * @since 0.1.0
 */
public class JsonTraverse {

    private static final ThreadLocal<JsonTree> tlJsonTree = ThreadLocal.withInitial(JsonTree::new);

    public static Object traverse(Object json, BMap<BString, Object> options, Type type) {
        JsonTree jsonTree = tlJsonTree.get();
        try {
            Object allowDataProjection = options.get(Constants.ALLOW_DATA_PROJECTION);
            if (allowDataProjection instanceof Boolean) {
                jsonTree.allowDataProjection = false;
            } else if (allowDataProjection instanceof BMap<?, ?>) {
                jsonTree.allowDataProjection = true;
                jsonTree.absentAsNilableType =
                        (Boolean) ((BMap<?, ?>) allowDataProjection).get(Constants.ABSENT_AS_NILABLE_TYPE);
                jsonTree.nilAsOptionalField =
                        (Boolean) ((BMap<?, ?>) allowDataProjection).get(Constants.NIL_AS_OPTIONAL_FIELD);
            }
            return jsonTree.traverseJson(json, type);
        } finally {
            jsonTree.reset();
        }
    }

    public static Object traverse(Object json, BMap<BString, Object> options, BTypedesc typed) {
        Object convertedValue = traverse(json, options, typed.getDescribingType());
        if (convertedValue instanceof BError) {
            return convertedValue;
        }
        return DataUtils.validateConstraints(convertedValue, typed,
                (Boolean) options.get(ENABLE_CONSTRAINT_VALIDATION));
    }

    private static class JsonTree {
        Field currentField;
        Stack<Map<String, Field>> fieldHierarchy = new Stack<>();
        Stack<Type> restType = new Stack<>();
        Deque<String> fieldNames = new ArrayDeque<>();
        Type rootArray;
        boolean allowDataProjection = false;
        boolean nilAsOptionalField = false;
        boolean absentAsNilableType = false;

        void reset() {
            currentField = null;
            fieldHierarchy.clear();
            restType.clear();
            fieldNames.clear();
            rootArray = null;
            allowDataProjection = false;
            nilAsOptionalField = false;
            absentAsNilableType = false;
        }

        private Object traverseJson(Object json, Type type) {
            Type referredType = TypeUtils.getReferredType(type);
            if (json == null && referredType.isNilable()) {
                return null;
            }
            TypeParser tp = new TypeParser();

            switch (referredType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG -> {
                    if (!(json instanceof BMap)) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, json);
                    }
                    RecordType recordType = (RecordType) referredType;
                    fieldHierarchy.push(JsonCreator.getAllFieldsInRecord(recordType));
                    restType.push(recordType.getRestFieldType());
                    if (recordType.isReadOnly()) {
                        Object value = traverseMapJsonOrArrayJson(json,
                                        ValueCreator.createMapValue(TypeCreator
                                                .createMapType(PredefinedTypes.TYPE_ANYDATA)), referredType);
                        try {
                            return ValueUtils.convert(JsonCreator.constructReadOnlyValue(value), recordType);
                        } catch (BError e) {
                            throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, json);
                        }
                    }
                    return traverseMapJsonOrArrayJson(json,
                            ValueCreator.createRecordValue(type.getPackage(), type.getName()), type);
                }
                case TypeTags.ARRAY_TAG -> {
                    if (!(json instanceof BArray)) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, json);
                    }
                    rootArray = referredType;
                    return traverseMapJsonOrArrayJson(json, ValueCreator.createArrayValue((ArrayType) referredType),
                            type);
                }
                case TypeTags.TUPLE_TAG -> {
                    if (!(json instanceof BArray)) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, json);
                    }
                    rootArray = referredType;
                    return traverseMapJsonOrArrayJson(json, ValueCreator.createTupleValue((TupleType) referredType),
                            type);
                }
                case TypeTags.STRING_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG, TypeTags.BOOLEAN_TAG,
                        TypeTags.NEVER_TAG, TypeTags.NULL_TAG, TypeTags.FINITE_TYPE_TAG -> {
                    Object convertedValue = convertToBasicType(json, referredType);
                    if (convertedValue instanceof BError) {
                        return convertedValue;
                    }
                    // Only validate annotation-driven constraints (not structural type keyword)
                    LinkedHashMap<String, Keyword> basicKeywords = tp.extractAnnotationKeywords(type);
                    if (!basicKeywords.isEmpty()) {
                        EvaluationContext context = new EvaluationContext();
                        boolean valid = true;
                        for (Keyword kw : basicKeywords.values()) {
                            if (!kw.evaluate(convertedValue, context)) {
                                valid = false;
                            }
                        }
                        if (!valid || !context.getErrors().isEmpty()) {
                            String errorMessage = String.join("; ", context.getErrors());
                            throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED, errorMessage);
                        }
                    }
                    return convertedValue;
                }
                case TypeTags.CHAR_STRING_TAG , TypeTags.BYTE_TAG,
                        TypeTags.UNSIGNED8_INT_TAG, TypeTags.UNSIGNED16_INT_TAG, TypeTags.UNSIGNED32_INT_TAG,
                        TypeTags.SIGNED8_INT_TAG, TypeTags.SIGNED16_INT_TAG, TypeTags.SIGNED32_INT_TAG -> {
                    return convertToBasicType(json, referredType);
                }
                case TypeTags.UNION_TAG -> {
                    UnionType unionType = (UnionType) referredType;
                    if (json == null && unionType.isNilable()) {
                        return null;
                    }

                    for (Type memberType : unionType.getMemberTypes()) {
                        try {
                            return traverseJson(json, memberType);
                        } catch (Exception e) {
                            // Ignore
                        }
                    }
                    throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, json);
                }
                case TypeTags.JSON_TAG -> {
                    // Only validate annotation-driven constraints (not structural keywords)
                    LinkedHashMap<String, Keyword> jsonKeywords = tp.extractAnnotationKeywords(type);
                    if (!jsonKeywords.isEmpty()) {
                        EvaluationContext context = new EvaluationContext();
                        boolean valid = true;
                        for (Keyword kw : jsonKeywords.values()) {
                            if (!kw.evaluate(json, context)) {
                                valid = false;
                            }
                        }
                        if (!valid || !context.getErrors().isEmpty()) {
                            String errorMessage = String.join("; ", context.getErrors());
                            throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED, errorMessage);
                        }
                    }
                    return json;
                }
                case TypeTags.ANYDATA_TAG -> {
                    return json;
                }
                case TypeTags.MAP_TAG -> {
                    MapType mapType = (MapType) referredType;
                    fieldHierarchy.push(new HashMap<>());
                    restType.push(mapType.getConstrainedType());
                    return traverseMapJsonOrArrayJson(json, ValueCreator.createMapValue(mapType), referredType);
                }
                case TypeTags.INTERSECTION_TAG -> {
                    Type effectiveType = ((IntersectionType) referredType).getEffectiveType();
                    if (!effectiveType.isReadOnly()) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE, type);
                    }
                    for (Type constituentType : ((IntersectionType) referredType).getConstituentTypes()) {
                        if (constituentType.getTag() == TypeTags.READONLY_TAG) {
                            continue;
                        }
                        return JsonCreator.constructReadOnlyValue(traverseJson(json, constituentType));
                    }
                    throw DiagnosticLog.error(DiagnosticErrorCode.UNSUPPORTED_TYPE, type);
                }
                default ->
                        throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, type, PredefinedTypes.TYPE_ANYDATA);
            }
        }

        private Object traverseMapJsonOrArrayJson(Object json, Object currentJsonNode, Type type) {
            if (json instanceof BMap bMap) {
                return traverseMapValue(bMap, currentJsonNode, type);
            } else if (json instanceof BArray bArray) {
                return traverseArrayValue(bArray, currentJsonNode, type);
            } else {
                // JSON value not compatible with map or array.
                Type refType = TypeUtils.getReferredType(type);
                if (refType.getTag() == TypeTags.RECORD_TYPE_TAG) {
                    this.fieldHierarchy.pop();
                    this.restType.pop();
                }

                if (fieldNames.isEmpty()) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, refType, json);
                }
                throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE_FOR_FIELD, getCurrentFieldPath());
            }
        }

        private Object traverseMapValue(BMap<BString, Object> map, Object currentJsonNode, Type type) {
            // Extract annotation-driven keywords for incremental validation
            TypeParser typeParser = new TypeParser();
            LinkedHashMap<String, Keyword> keywords = typeParser.extractAnnotationKeywords(type);
            
            // Skip validation if there are no annotation keywords
            boolean hasValidation = !keywords.isEmpty();
            
            List<IncrementalKeyword> incrementalKeywords = new ArrayList<>();
            List<IncrementalKeyword> allKeysKeywords = new ArrayList<>();
            List<IncrementalKeyword> restOnlyKeywords = new ArrayList<>();
            List<Keyword> nonIncrementalKeywords = new ArrayList<>();
            EvaluationContext context = null;
            boolean allValid = true;
            
            if (hasValidation) {
                // Partition into incremental and non-incremental keywords
                for (Keyword kw : keywords.values()) {
                    if (kw instanceof IncrementalKeyword) {
                        incrementalKeywords.add((IncrementalKeyword) kw);
                    } else {
                        nonIncrementalKeywords.add(kw);
                    }
                }
                
                // Sort incremental keywords by phase (PRIMARY -> DEPENDENT -> INDEPENDENT)
                incrementalKeywords.sort(Comparator.comparingInt(k -> k.getEvaluationPhase().getOrder()));

                // Partition incremental keywords into all-keys vs rest-only
                // PropertyNamesKeyword validates key names and should see ALL keys (including declared fields)
                // PatternProperties/AdditionalProperties only apply to rest-field keys
                for (IncrementalKeyword kw : incrementalKeywords) {
                    if (kw instanceof PropertyNamesKeyword) {
                        allKeysKeywords.add(kw);
                    } else {
                        restOnlyKeywords.add(kw);
                    }
                }

                // Create evaluation context
                context = new EvaluationContext();
                
                // Call begin() on all incremental keywords
                for (IncrementalKeyword kw : incrementalKeywords) {
                    kw.begin(map, context);
                }
                
                // Validate non-incremental keywords (minProperties, maxProperties, etc.)
                for (Keyword kw : nonIncrementalKeywords) {
                    if (!kw.evaluate(map, context)) {
                        allValid = false;
                    }
                }
            }
            
            // Iterate over map keys for type conversion AND incremental validation
            for (BString key : map.getKeys()) {
                String keyStr = key.getValue();
                Object mapValue = map.get(key);
                
                // PropertyNamesKeyword sees ALL keys (including declared fields)
                if (hasValidation) {
                    for (IncrementalKeyword kw : allKeysKeywords) {
                        kw.acceptElement(keyStr, mapValue, -1, context);
                    }
                }
                
                // Now perform the existing type conversion logic
                currentField = fieldHierarchy.peek().remove(key.toString());
                if (currentField == null) {
                    // Rest-field: PatternProperties/AdditionalProperties see only these keys
                    if (hasValidation) {
                        for (IncrementalKeyword kw : restOnlyKeywords) {
                            kw.acceptElement(keyStr, mapValue, -1, context);
                        }
                    }
                    
                    // Add to the rest field
                    if (restType.peek() != null) {
                        Type restFieldType = TypeUtils.getReferredType(restType.peek());
                        addRestField(restFieldType, key, map.get(key), currentJsonNode);
                        continue;
                    }
                    if (allowDataProjection) {
                        continue;
                    }
                    this.fieldHierarchy.pop();
                    this.restType.pop();
                    throw DiagnosticLog.error(DiagnosticErrorCode.UNDEFINED_FIELD, key);
                }

                String fieldName = currentField.getFieldName();
                fieldNames.push(fieldName);
                Type currentFieldType = TypeUtils.getReferredType(currentField.getFieldType());
                int currentFieldTypeTag = currentFieldType.getTag();

                if (nilAsOptionalField && !currentFieldType.isNilable() && mapValue == null
                        && SymbolFlags.isFlagOn(currentField.getFlags(), SymbolFlags.OPTIONAL)) {
                    continue;
                }

                switch (currentFieldTypeTag) {
                    case TypeTags.NULL_TAG, TypeTags.BOOLEAN_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG,
                            TypeTags.DECIMAL_TAG, TypeTags.STRING_TAG -> {
                        Object value = convertToBasicType(mapValue, currentFieldType);
                        ((BMap<BString, Object>) currentJsonNode).put(StringUtils.fromString(fieldNames.pop()), value);
                    }
                    default ->
                        ((BMap<BString, Object>) currentJsonNode).put(StringUtils.fromString(fieldName),
                                traverseJson(mapValue, currentFieldType));
                }
            }
            
            Map<String, Field> currentField = fieldHierarchy.pop();
            checkOptionalFieldsAndLogError(currentField);
            restType.pop();
            
            // Call finish() on all incremental keywords after conversion is complete
            if (hasValidation) {
                for (IncrementalKeyword kw : incrementalKeywords) {
                    if (!kw.finish(context)) {
                        allValid = false;
                    }
                }
                
                // Throw validation error AFTER conversion is complete (conversion first, validation second)
                if (!allValid) {
                    String errorMessage = String.join("; ", context.getErrors());
                    throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED, errorMessage);
                }
            }
            
            return currentJsonNode;
        }

        private Object traverseArrayValue(BArray array, Object currentJsonNode, Type type) {
            // Extract annotation-driven keywords for incremental validation
            TypeParser typeParser = new TypeParser();
            LinkedHashMap<String, Keyword> keywords = typeParser.extractAnnotationKeywords(type);

            // Skip validation if there are no annotation keywords
            boolean hasValidation = !keywords.isEmpty();
            
            List<IncrementalKeyword> incrementalKeywords = new ArrayList<>();
            List<Keyword> nonIncrementalKeywords = new ArrayList<>();
            EvaluationContext context = null;
            boolean allValid = true;
            
            if (hasValidation) {
                // Partition into incremental and non-incremental keywords
                for (Keyword kw : keywords.values()) {
                    if (kw instanceof IncrementalKeyword) {
                        incrementalKeywords.add((IncrementalKeyword) kw);
                    } else {
                        nonIncrementalKeywords.add(kw);
                    }
                }
                
                // Sort incremental keywords by phase (PRIMARY -> DEPENDENT -> INDEPENDENT)
                incrementalKeywords.sort(Comparator.comparingInt(k -> k.getEvaluationPhase().getOrder()));

                // Create evaluation context
                context = new EvaluationContext();
                
                // Call begin() on all incremental keywords
                for (IncrementalKeyword kw : incrementalKeywords) {
                    kw.begin(array, context);
                }
                
                // Validate non-incremental keywords (minItems, maxItems, etc.)
                for (Keyword kw : nonIncrementalKeywords) {
                    if (!kw.evaluate(array, context)) {
                        allValid = false;
                    }
                }
            }
            
            switch (rootArray.getTag()) {
                case TypeTags.ARRAY_TAG -> {
                    ArrayType arrayType = (ArrayType) rootArray;
                    int expectedArraySize = arrayType.getSize();
                    long sourceArraySize = array.getLength();
                    if (!allowDataProjection && arrayType.getState() == ArrayType.ArrayState.CLOSED
                            && expectedArraySize < sourceArraySize) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
                    }

                    Type elementType = arrayType.getElementType();
                    if (expectedArraySize == -1 || expectedArraySize > sourceArraySize) {
                        traverseArrayMembers(array.getLength(), array, elementType, currentJsonNode,
                                hasValidation, incrementalKeywords, context);
                    } else {
                        traverseArrayMembers(expectedArraySize, array, elementType, currentJsonNode,
                                hasValidation, incrementalKeywords, context);
                    }
                }
                case TypeTags.TUPLE_TAG -> {
                    TupleType tupleType = (TupleType) rootArray;
                    Type restType = tupleType.getRestType();
                    int expectedTupleTypeCount = tupleType.getTupleTypes().size();
                    for (int i = 0; i < array.getLength(); i++) {
                        Object jsonMember = array.get(i);
                        
                        // Call acceptElement() on all incremental keywords in phase order (if any)
                        if (hasValidation) {
                            for (IncrementalKeyword kw : incrementalKeywords) {
                                kw.acceptElement(null, jsonMember, i, context);
                            }
                        }
                        
                        Object nextJsonNode;
                        if (i < expectedTupleTypeCount) {
                            nextJsonNode = traverseJson(jsonMember, tupleType.getTupleTypes().get(i));
                        } else if (restType != null) {
                            nextJsonNode = traverseJson(jsonMember, restType);
                        } else if (!allowDataProjection) {
                            throw DiagnosticLog.error(DiagnosticErrorCode.ARRAY_SIZE_MISMATCH);
                        } else {
                            continue;
                        }
                        ((BArray) currentJsonNode).add(i, nextJsonNode);
                    }
                }
            }
            
            // Call finish() on all incremental keywords after conversion is complete
            if (hasValidation) {
                for (IncrementalKeyword kw : incrementalKeywords) {
                    if (!kw.finish(context)) {
                        allValid = false;
                    }
                }
                
                // Throw validation error AFTER conversion is complete (conversion first, validation second)
                if (!allValid) {
                    String errorMessage = String.join("; ", context.getErrors());
                    throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED, errorMessage);
                }
            }
            
            return currentJsonNode;
        }

        private void traverseArrayMembers(long length, BArray array, Type elementType, Object currentJsonNode,
                                          boolean hasValidation, List<IncrementalKeyword> incrementalKeywords, 
                                          EvaluationContext context) {
            for (int i = 0; i < length; i++) {
                Object jsonMember = array.get(i);
                
                // Call acceptElement() on all incremental keywords in phase order (if any)
                if (hasValidation) {
                    for (IncrementalKeyword kw : incrementalKeywords) {
                        kw.acceptElement(null, jsonMember, i, context);
                    }
                }
                
                ((BArray) currentJsonNode).add(i, traverseJson(jsonMember, elementType));
            }
        }

        private void addRestField(Type restFieldType, BString key, Object jsonMember, Object currentJsonNode) {
            Object nextJsonValue;
            switch (restFieldType.getTag()) {
                case TypeTags.ANYDATA_TAG, TypeTags.JSON_TAG ->
                        ((BMap<BString, Object>) currentJsonNode).put(key, jsonMember);
                case TypeTags.BOOLEAN_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG,
                        TypeTags.STRING_TAG -> {
                    ((BMap<BString, Object>) currentJsonNode).put(key, convertToBasicType(jsonMember, restFieldType));
                }
                default -> {
                    nextJsonValue = traverseJson(jsonMember, restFieldType);
                    ((BMap<BString, Object>) currentJsonNode).put(key, nextJsonValue);
                }
            }
        }

        private void checkOptionalFieldsAndLogError(Map<String, Field> currentField) {
            currentField.values().forEach(field -> {
                if (field.getFieldType().isNilable() && absentAsNilableType) {
                    return;
                }
                if (SymbolFlags.isFlagOn(field.getFlags(), SymbolFlags.REQUIRED)) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.REQUIRED_FIELD_NOT_PRESENT, field.getFieldName());
                }
            });
        }

        private Object convertToBasicType(Object json, Type targetType) {
            try {
                return ValueUtils.convert(json, targetType);
            } catch (BError e) {
                if (fieldNames.isEmpty()) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, targetType, String.valueOf(json));
                }
                throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_VALUE_FOR_FIELD, String.valueOf(json),
                        targetType, getCurrentFieldPath());
            }
        }

        private String getCurrentFieldPath() {
            Iterator<String> itr = fieldNames.descendingIterator();
            StringBuilder sb = new StringBuilder(itr.hasNext() ? itr.next() : "");
            while (itr.hasNext()) {
                sb.append(".").append(itr.next());
            }
            return sb.toString();
        }
    }
}

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
import io.ballerina.lib.data.jsondata.json.schema.SchemaTypeParser;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.AdditionalPropertiesKeyword;
import io.ballerina.lib.data.jsondata.utils.Constants;
import io.ballerina.lib.data.jsondata.utils.DataUtils;
import io.ballerina.lib.data.jsondata.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.lib.data.jsondata.utils.SchemaValidatorUtils;
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

import java.util.*;

import static io.ballerina.lib.data.jsondata.utils.Constants.ENABLE_CONSTRAINT_VALIDATION;

/**
 * Traverse json tree.
 *
 * @since 0.1.0
 */
public class JsonTraverse {

    private static final ThreadLocal<JsonTree> tlJsonTree = ThreadLocal.withInitial(JsonTree::new);
    private static final SchemaTypeParser schemaTypeParser = new SchemaTypeParser();

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
        Stack<EvaluationContext> allOfAccumulatorStack = new Stack<>();

        void reset() {
            currentField = null;
            fieldHierarchy.clear();
            restType.clear();
            fieldNames.clear();
            rootArray = null;
            allowDataProjection = false;
            nilAsOptionalField = false;
            absentAsNilableType = false;
            allOfAccumulatorStack.clear();
        }

        private Object traverseJson(Object json, Type type) {
            Type referredType = TypeUtils.getReferredType(type);
            if (json == null && referredType.isNilable()) {
                return null;
            }

            switch (referredType.getTag()) {
                case TypeTags.RECORD_TYPE_TAG -> {
                    if (!(json instanceof BMap)) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, json);
                    }
                    RecordType recordType = (RecordType) referredType;

                    if (validateJsonSchemaAnnotations(json, referredType) instanceof BError validationError) {
                        throw validationError;
                    }

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
                            ValueCreator.createRecordValue(type.getPackage(), type.getName()), referredType);
                }
                case TypeTags.ARRAY_TAG -> {

                    if (validateJsonSchemaAnnotations(json, type) instanceof BError validationError) {
                        throw validationError;
                    }

                    if (!(json instanceof BArray)) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, json);
                    }
                    rootArray = referredType;
                    return traverseMapJsonOrArrayJson(json, ValueCreator.createArrayValue((ArrayType) referredType),
                            referredType);
                }
                case TypeTags.TUPLE_TAG -> {

                    if (validateJsonSchemaAnnotations(json, type) instanceof BError validationError) {
                        throw validationError;
                    }

                    if (!(json instanceof BArray)) {
                        throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, json);
                    }
                    rootArray = referredType;
                    return traverseMapJsonOrArrayJson(json, ValueCreator.createTupleValue((TupleType) referredType),
                            referredType);
                }
                case TypeTags.NULL_TAG, TypeTags.BOOLEAN_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG,
                     TypeTags.DECIMAL_TAG, TypeTags.STRING_TAG, TypeTags.CHAR_STRING_TAG , TypeTags.BYTE_TAG,
                     TypeTags.SIGNED8_INT_TAG, TypeTags.SIGNED16_INT_TAG, TypeTags.SIGNED32_INT_TAG,
                     TypeTags.UNSIGNED8_INT_TAG, TypeTags.UNSIGNED16_INT_TAG, TypeTags.UNSIGNED32_INT_TAG,
                     TypeTags.FINITE_TYPE_TAG -> {

                    if (validateJsonSchemaAnnotations(json, type) instanceof BError validationError) {
                        throw validationError;
                    }

                    return convertToBasicType(json, referredType);
                }
                case TypeTags.UNION_TAG -> {
                    UnionType unionType = (UnionType) referredType;
                    if (json == null && unionType.isNilable()) {
                        return null;
                    }

                    LinkedHashMap<String, Keyword> keywords =
                            schemaTypeParser.extractAnnotationKeywords(type);

                    Keyword notKeyword = keywords.get("not");
                    if (notKeyword != null) {
                        Object notSchemaValue = notKeyword.getKeywordValue();
                        if (notSchemaValue instanceof Schema notSchema) {
                            EvaluationContext notCtx = new EvaluationContext();
                            if (notKeyword.evaluate(json, notCtx)) {
                                throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED, String.join("\n", notCtx.getErrors()));
                            }
                        }
                    }

                    boolean hasAllOf = keywords.containsKey("allOf");
                    boolean hasOneOf = keywords.containsKey("oneOf");

                    if (hasAllOf || hasOneOf) {
                        allOfAccumulatorStack.push(new EvaluationContext());
                    }

                    int matchCount = 0;
                    Object parsed = null;

                    for (Type memberType : unionType.getMemberTypes()) {
                        int fieldHierarchySize = fieldHierarchy.size();
                        int restTypeSize = restType.size();
                        int fieldNamesSize = fieldNames.size();
                        try {
                            parsed = traverseJson(json, memberType);
                            matchCount++;
                            if (hasOneOf && matchCount > 1) {
                                throw DiagnosticLog.error(
                                        DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED, "oneOf matched more than one member type");
                            }

                            if (!hasOneOf && !hasAllOf) {
                                return parsed;
                            }
                        } catch (Exception e) {
                            while (fieldHierarchy.size() > fieldHierarchySize) {
                                fieldHierarchy.pop();
                            }
                            while (restType.size() > restTypeSize) {
                                restType.pop();
                            }
                            while (fieldNames.size() > fieldNamesSize) {
                                fieldNames.pollLast();
                            }
                            if (hasAllOf) {
                                throw DiagnosticLog.error(
                                        DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED, "allOf member type failed: " + e.getMessage());
                            }
                        }
                    }
                    if (hasAllOf || hasOneOf) {
                        allOfAccumulatorStack.pop();
                    }
                    if (parsed != null && !(parsed instanceof BError)) {
                        return parsed;
                    }
                    throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, json);
                }
                case TypeTags.JSON_TAG, TypeTags.ANYDATA_TAG -> {
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
                return traverseMapValue(bMap, currentJsonNode);
            } else if (json instanceof BArray bArray) {
                return traverseArrayValue(bArray, currentJsonNode);
            } else {
                // JSON value not compatible with map or array.
                if (type.getTag() == TypeTags.RECORD_TYPE_TAG) {
                    this.fieldHierarchy.pop();
                    this.restType.pop();
                }

                if (fieldNames.isEmpty()) {
                    throw DiagnosticLog.error(DiagnosticErrorCode.INCOMPATIBLE_TYPE, type, json);
                }
                throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE_FOR_FIELD, getCurrentFieldPath());
            }
        }

        private Object traverseMapValue(BMap<BString, Object> map, Object currentJsonNode) {
            for (BString key : map.getKeys()) {
                currentField = fieldHierarchy.peek().remove(key.toString());
                if (currentField == null) {
                    // Add to the rest field
                    if (restType.peek() != null) {
                        addRestField(restType.peek(), key, map.get(key), currentJsonNode);
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
                Type currentFieldType = currentField.getFieldType();
                int currentFieldTypeTag = TypeUtils.getReferredType(currentFieldType).getTag();
                Object mapValue = map.get(key);

                if (nilAsOptionalField && !TypeUtils.getReferredType(currentFieldType).isNilable() && mapValue == null
                        && SymbolFlags.isFlagOn(currentField.getFlags(), SymbolFlags.OPTIONAL)) {
                    continue;
                }

                switch (currentFieldTypeTag) {
                    case TypeTags.NULL_TAG, TypeTags.BOOLEAN_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG,
                         TypeTags.DECIMAL_TAG, TypeTags.STRING_TAG -> {
                        if (validateJsonSchemaAnnotations(mapValue, currentFieldType) instanceof BError validationError) {
                            throw validationError;
                        }
                        Type resolvedType = TypeUtils.getReferredType(currentFieldType);
                        Object value = convertToBasicType(mapValue, resolvedType);
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
            return currentJsonNode;
        }

        private Object traverseArrayValue(BArray array, Object currentJsonNode) {
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
                        traverseArrayMembers(array.getLength(), array, elementType, currentJsonNode);
                    } else {
                        traverseArrayMembers(expectedArraySize, array, elementType, currentJsonNode);
                    }
                }
                case TypeTags.TUPLE_TAG -> {
                    TupleType tupleType = (TupleType) rootArray;
                    Type restType = tupleType.getRestType();
                    int expectedTupleTypeCount = tupleType.getTupleTypes().size();
                    for (int i = 0; i < array.getLength(); i++) {
                        Object jsonMember = array.get(i);
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
            return currentJsonNode;
        }

        private void traverseArrayMembers(long length, BArray array, Type elementType, Object currentJsonNode) {
            for (int i = 0; i < length; i++) {
                ((BArray) currentJsonNode).add(i, traverseJson(array.get(i), elementType));
            }
        }

        private void addRestField(Type restFieldType, BString key, Object jsonMember, Object currentJsonNode) {
            Object nextJsonValue;
            Type resolvedType = TypeUtils.getReferredType(restFieldType);
            switch (resolvedType.getTag()) {
                case TypeTags.ANYDATA_TAG, TypeTags.JSON_TAG ->
                        ((BMap<BString, Object>) currentJsonNode).put(key, jsonMember);
                case TypeTags.BOOLEAN_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG, TypeTags.STRING_TAG -> {
                    if (validateJsonSchemaAnnotations(jsonMember, restFieldType)
                            instanceof BError validationError) {
                        throw validationError;
                    }
                    ((BMap<BString, Object>) currentJsonNode).put(key, convertToBasicType(jsonMember, resolvedType));
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

        private Object validateJsonSchemaAnnotations(Object json, Type referredType) {
            EvaluationContext ctx = new EvaluationContext();
            Type refType = TypeUtils.getReferredType(referredType);
            LinkedHashMap<String, Keyword> keywords = schemaTypeParser.extractAnnotationKeywords(referredType);

            if (!allOfAccumulatorStack.isEmpty()) {
                EvaluationContext accumulator = allOfAccumulatorStack.peek();
                copyAnnotation(accumulator, ctx, "evaluatedItems");
                copyAnnotation(accumulator, ctx, "evaluatedProperties");
            }

            presetTypeTraversalAnnotations(ctx, refType, keywords);

            if (!keywords.isEmpty()) {
                Schema schemaWithAnnotations = new Schema(keywords);
                boolean validationResult = Validator.validate(json, schemaWithAnnotations, ctx);
                if (!allOfAccumulatorStack.isEmpty()) {
                    EvaluationContext accumulator = allOfAccumulatorStack.peek();
                    SchemaValidatorUtils.createEvaluatedItemsAnnotation(ctx);
                    SchemaValidatorUtils.createEvaluatedPropertiesAnnotation(ctx);
                    mergeAnnotation(accumulator, ctx, "evaluatedItems");
                    mergeAnnotation(accumulator, ctx, "evaluatedProperties");
                }
                if (!validationResult) {
                    return DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED, String.join("\n", ctx.getErrors()));
                }
            } else if (!allOfAccumulatorStack.isEmpty()) {
                EvaluationContext accumulator = allOfAccumulatorStack.peek();
                SchemaValidatorUtils.createEvaluatedItemsAnnotation(ctx);
                SchemaValidatorUtils.createEvaluatedPropertiesAnnotation(ctx);
                mergeAnnotation(accumulator, ctx, "evaluatedItems");
                mergeAnnotation(accumulator, ctx, "evaluatedProperties");
            }
            return null;
        }

        private void presetTypeTraversalAnnotations(EvaluationContext ctx, Type refType,
                                                    LinkedHashMap<String, Keyword> keywords) {
            switch (refType.getTag()) {
                case TypeTags.ARRAY_TAG -> {
                    if (keywords.containsKey("unevaluatedItems")) {
                        ArrayType arrayType = (ArrayType) refType;
                        Type elementType = TypeUtils.getReferredType(arrayType.getElementType());
                        if (elementType.getTag() != TypeTags.JSON_TAG && elementType.getTag() != TypeTags.ANYDATA_TAG) {
                            ctx.setAnnotation("items", true);
                        }
                    }
                }
                case TypeTags.TUPLE_TAG -> {
                    if (keywords.containsKey("unevaluatedItems")) {
                        TupleType tupleType = (TupleType) refType;
                        List<Type> tupleTypes = tupleType.getTupleTypes();
                        if (!tupleTypes.isEmpty()) {
                            ctx.setAnnotation("prefixItems", (long) (tupleTypes.size() - 1));
                        }
                        Type restType = tupleType.getRestType();
                        if (restType != null) {
                            Type resolvedRest = TypeUtils.getReferredType(restType);
                            if (resolvedRest.getTag() != TypeTags.JSON_TAG
                                    && resolvedRest.getTag() != TypeTags.ANYDATA_TAG) {
                                ctx.setAnnotation("items", true);
                            }
                        }
                    }
                }
                case TypeTags.RECORD_TYPE_TAG -> {
                    if (keywords.containsKey("unevaluatedProperties")) {
                        RecordType recordType = (RecordType) refType;
                        Type restFieldType = recordType.getRestFieldType();
                        if (restFieldType != null && !keywords.containsKey("additionalProperties")) {
                            Object restSchema = schemaTypeParser.parse(restFieldType);
                            if (!(restSchema instanceof BError)) {
                                keywords.put(AdditionalPropertiesKeyword.keywordName,
                                        new AdditionalPropertiesKeyword(restSchema));
                            }
                        }
                    }
                }
            }
        }

        private static void copyAnnotation(EvaluationContext src, EvaluationContext dst, String key) {
            Object val = src.getAnnotation(key);
            if (val != null) {
                dst.setAnnotation(key, val);
            }
        }

        private static void mergeAnnotation(EvaluationContext target, EvaluationContext source, String key) {
            Object sourceVal = source.getAnnotation(key);
            if (sourceVal == null) {
                return;
            }
            Object targetVal = target.getAnnotation(key);
            if (targetVal == null) {
                target.setAnnotation(key, sourceVal);
                return;
            }
            if (sourceVal instanceof Boolean b && b) {
                target.setAnnotation(key, true);
                return;
            }
            if (targetVal instanceof Boolean b && b) {
                return;
            }
            if (sourceVal instanceof List<?> srcList && targetVal instanceof List<?> tgtList) {
                Set<Object> merged = new LinkedHashSet<>(tgtList);
                merged.addAll(srcList);
                target.setAnnotation(key, new ArrayList<>(merged));
                return;
            }
            if (sourceVal instanceof Set && targetVal instanceof Set) {
                ((Set<Object>) targetVal).addAll((Set<?>) sourceVal);
            }
        }

    }
}

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
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.AnyOfKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.AllOfKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.OneOfKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.ItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PrefixItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PropertiesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.AdditionalPropertiesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PropertyNamesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PatternPropertiesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.DependentSchemasKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.*;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.DependentRequiredKeyword;
import io.ballerina.lib.data.jsondata.utils.Constants;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.AnnotatableType;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.Field;
import io.ballerina.runtime.api.types.FiniteType;
import io.ballerina.runtime.api.types.IntersectionType;
import io.ballerina.runtime.api.types.PredefinedTypes;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.TupleType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BRegexpValue;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.api.values.BTypedesc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class TypeParser {
    private final HashMap<String, Object> typeAliasToSchema = new HashMap<String, Object>();

    public Object parse(Type type) {
        if (typeAliasToSchema.containsKey(type.getName())) {
            return typeAliasToSchema.get(type.getName());
        }

        if (type.getTag() == TypeTags.TYPE_REFERENCED_TYPE_TAG) {
            typeAliasToSchema.put(type.getName(), new Schema());
        }

        Type referredType = TypeUtils.getReferredType(type);

        Object schema = switch (referredType.getTag()) {
            case TypeTags.UNION_TAG -> parseUnionType(type);
            case TypeTags.TUPLE_TAG, TypeTags.ARRAY_TAG -> parseArrayType(type);
            case TypeTags.STRING_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG, TypeTags.BOOLEAN_TAG,
                 TypeTags.JSON_TAG, TypeTags.NEVER_TAG, TypeTags.NULL_TAG, TypeTags.FINITE_TYPE_TAG ->
                    parseBasicType(type);
            case TypeTags.RECORD_TYPE_TAG -> parseRecordType(type);
            default -> DiagnosticLog.createJsonError("unsupported type: " + referredType);
        };

        if (schema instanceof BError) {
            return schema;
        }

        if (type.getTag() == TypeTags.TYPE_REFERENCED_TYPE_TAG) {
            Schema cachedSchema = (Schema) typeAliasToSchema.get(type.getName());
            cachedSchema.setKeywords(((Schema) schema).getKeywords());
            return cachedSchema;
        }

        return schema;
    }

    public TypeKeyword extractTypeKeyword(ArrayList<Type> type) {
        TypeKeyword typeKeyword = null;
        Set<String> typeNames = new HashSet<>();
        for (Type memberType : type) {
            Type referredType = TypeUtils.getReferredType(memberType);
            switch (referredType.getTag()) {
                case TypeTags.STRING_TAG -> typeNames.add("string");
                case TypeTags.INT_TAG -> {
                    if (!typeNames.contains("number")) {
                        typeNames.add("integer");
                    }
                }
                case TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG -> {
                    typeNames.remove("integer");
                    typeNames.add("number");
                }
                case TypeTags.BOOLEAN_TAG -> typeNames.add("boolean");
                case TypeTags.JSON_TAG -> typeNames.add("object");
                case TypeTags.NEVER_TAG -> typeNames.add("never");
                case TypeTags.NULL_TAG -> typeNames.add("null");
                default -> {}
            }
        }
        if (!typeNames.isEmpty()) {
            typeKeyword = new TypeKeyword(typeNames);
        }

        if (typeNames.contains("number")) {
            typeNames.remove("integer");
        }
        return typeKeyword;
    }

    public Object parseUnionType(Type type) {
        Type referredType = TypeUtils.getReferredType(type);
        if (!(referredType instanceof UnionType unionType)) {
            return null;
        }
        LinkedHashMap<String, Keyword> keywords = new LinkedHashMap<>();
        extractKeywordsFromAnnotations(type, keywords);

        List<Type> memberTypes = unionType.getOriginalMemberTypes();
//        System.out.println("Parsing union type: " + referredType + ", with members: " + memberTypes);

        ArrayList<Type> basicTypeMembers = new ArrayList<>();
        ArrayList<Type> constValues = new ArrayList<>();
        List<Object> memberSchemas = new ArrayList<>();

        for (Type memberType : memberTypes) {
            if (memberType.getTag() == TypeTags.TYPE_REFERENCED_TYPE_TAG || memberType.getTag() == TypeTags.ARRAY_TAG) {
                Object parsedMember = parse(memberType);
                if (parsedMember instanceof Schema || parsedMember instanceof Boolean) {
                    memberSchemas.add(parsedMember);
                } else {
                    return (BError) parsedMember;
                }
            } else if (memberType.getTag() == TypeTags.TUPLE_TAG) {
                ArrayList<Type> tupleMembers = new ArrayList<>(((TupleType) memberType).getTupleTypes());
                boolean allConstOrEnum = true;
                for (Type tupleMember : tupleMembers) {
                    Type referredTupleMember = TypeUtils.getReferredType(tupleMember);
                    if (!(referredTupleMember.getTag() == TypeTags.FINITE_TYPE_TAG) && !(referredTupleMember.getTag() == TypeTags.INTERSECTION_TAG)) {
                        allConstOrEnum = false;
                        break;
                    }
                }
                if (allConstOrEnum) {
                    constValues.add(memberType);
                } else {
                    Object parsedMember = parse(memberType);
                    if (parsedMember instanceof Schema || parsedMember instanceof Boolean) {
                        memberSchemas.add(parsedMember);
                    } else {
                        return (BError) parsedMember;
                    }
                }
            } else if (
                memberType.getTag() == TypeTags.INTERSECTION_TAG ||
                memberType.getTag() == TypeTags.FINITE_TYPE_TAG 
            ) {
                constValues.add(memberType);
            } else {
                basicTypeMembers.add(memberType);
            }
        }

        boolean isAllOf = keywords.containsKey(AllOfKeyword.keywordName);
        boolean isOneOf = keywords.containsKey(OneOfKeyword.keywordName);

        if (isAllOf || isOneOf) {
            List<Object> wrapperList = new ArrayList<>(memberSchemas);

            if (!basicTypeMembers.isEmpty()) {
                TypeKeyword typeKeyword = extractTypeKeyword(basicTypeMembers);
                if (typeKeyword != null) {
                    LinkedHashMap<String, Keyword> typeKeywords = new LinkedHashMap<>();
                    typeKeywords.put(TypeKeyword.keywordName, typeKeyword);
                    wrapperList.add(new Schema(typeKeywords));
                }
            }

            if (!constValues.isEmpty()) {
                LinkedHashMap<String, Keyword> constKeywords = new LinkedHashMap<>();
                extractConstOrEnumKeyword(constValues, constKeywords);
                if (!constKeywords.isEmpty()) {
                    wrapperList.add(new Schema(constKeywords));
                }
            }

            if (isAllOf) {
                keywords.put(AllOfKeyword.keywordName, new AllOfKeyword(wrapperList));
                keywords.remove(OneOfKeyword.keywordName);
            } else {
                keywords.put(OneOfKeyword.keywordName, new OneOfKeyword(wrapperList));
            }

        } else {
            if (!basicTypeMembers.isEmpty()) {
                TypeKeyword typeKeyword = extractTypeKeyword(basicTypeMembers);
                if (typeKeyword != null) {
                    keywords.put(TypeKeyword.keywordName, typeKeyword);
                }
            }
            extractConstOrEnumKeyword(constValues, keywords);

            if (!memberSchemas.isEmpty()) {
                keywords.put(AnyOfKeyword.keywordName, new AnyOfKeyword(memberSchemas));
            }
        }

        return new Schema(keywords);
    }

    public void extractConstOrEnumKeyword(ArrayList<Type> referredTypes, LinkedHashMap<String, Keyword> keywords) {
        Set<Object> constValues = new HashSet<>();
        for (Type type : referredTypes) {
            Object constValue = extractConstValues(type);
            if (constValue != null) {
                if (constValue instanceof Set) {
                    constValues.addAll((Set<?>) constValue);
                } else {
                    constValues.add(constValue);
                }
            }
        }
        if (constValues.size() == 1) {
            Object constValue = constValues.iterator().next();
            keywords.put(ConstKeyword.keywordName, new ConstKeyword(constValue));
        } else if (constValues.size() > 1) {
            keywords.put(EnumKeyword.keywordName, new EnumKeyword(constValues));
        }
    }

    private Object extractConstValues(Type type) {
        if (type.getTag() == TypeTags.INTERSECTION_TAG) {
            Type effectiveType = ((IntersectionType) type).getEffectiveType();
            return extractConstValues(effectiveType);
        }
        else if (type.getTag() == TypeTags.FINITE_TYPE_TAG) {
            FiniteType finiteType = (FiniteType) type;
            if (finiteType.getValueSpace().size() == 1) {
                return finiteType.getValueSpace().iterator().next();
            } else {
                return new HashSet<>(finiteType.getValueSpace());
            }
        } 
        else if (type.getTag() == TypeTags.RECORD_TYPE_TAG) {
            RecordType recordType = (RecordType) type;
            if ((recordType.getFlags() & SymbolFlags.READONLY) == SymbolFlags.READONLY) {
                BMap<BString, Object> bMap = ValueCreator.createMapValue(Constants.JSON_MAP_TYPE);
                for (Entry<String, Field> entry : recordType.getFields().entrySet()) {
                    String fieldName = entry.getKey();
                    Field field = entry.getValue();
                    Object fieldValue = extractConstValues(field.getFieldType());
                    bMap.put(StringUtils.fromString(fieldName), fieldValue);
                }
                return bMap;
            }
        }
        else if (type.getTag() == TypeTags.TUPLE_TAG) {
            TupleType tupleType = (TupleType) type;
            BArray bArray = ValueCreator.createArrayValue(
                    TypeCreator.createArrayType(PredefinedTypes.TYPE_ANY));

            int index = 0;
            for (Type memberType : tupleType.getTupleTypes()) {
                Object memberValue = extractConstValues(memberType);
                if (memberValue instanceof HashSet) {
                    for (Object item : (HashSet<?>) memberValue) {
                        bArray.add(index, item);
                        index += 1;
                    }
                }
                bArray.add(index, memberValue);
                index += 1;
            }
            return bArray;
        }

        return null;
    }

    public void extractKeywordsFromAnnotations(Type referredType, LinkedHashMap<String, Keyword> keywords) {
        if (keywords == null) {
            keywords = new LinkedHashMap<>();
        }

        if (!(referredType instanceof AnnotatableType annotatableType)) {
            return;
        }

        BMap<BString, Object> annotations = annotatableType.getAnnotations();

        if (annotations.isEmpty()) {
            return;
        }

        Pattern annotationNamePattern = Pattern.compile("([^:]+)$");

        for (BString key : annotations.getKeys()) {
            String annotationIdentifier = key.getValue();
            Matcher matcher = annotationNamePattern.matcher(annotationIdentifier);
            String annotationName = matcher.find() ? matcher.group(1) : annotationIdentifier;

            Object annotation = annotations.get(key);

            switch (annotationName) {
                case "StringConstraints":
                    extractStringValidationKeywords((BMap<BString, Object>) annotation, keywords);
                    break;
                case "NumberConstraints":
                    extractNumericValidationKeywords((BMap<BString, Object>) annotation, keywords);
                    break;
                case "ArrayConstraints":
                    extractArrayValidationKeywords((BMap<BString, Object>) annotation, keywords);
                    break;
                case "ObjectConstraints":
                    extractObjectValidationKeywords((BMap<BString, Object>) annotation, keywords);
                    break;
                case "PatternProperties":
                    extractPatternProperties((BMap<BString, Object>) annotation, keywords);
                    break;
                case "AdditionalProperties":
                    extractAdditionalProperties((BMap<BString, Object>) annotation, keywords);
                    break;
                case "ReadOnly":
                    break;
                case "WriteOnly":
                    break;
                case "MetaData":
                    break;
                case "StringEncodedData":
                    break;
                case "AllOf":
                    keywords.put(AllOfKeyword.keywordName, new AllOfKeyword(new ArrayList<>()));
                    break;
                case "OneOf":
                    keywords.put(OneOfKeyword.keywordName, new AllOfKeyword(new ArrayList<>()));
                    break;
                case "AnyOf":
                    break;
                case "Not":
                    break;
                case "UnevaluatedProperties":
                    break;
                default:
                    break;
            }
        }
    }

    public void extractKeywordsFromFieldAnnotations(Type type, LinkedHashMap<String, Keyword> keywords) {
        if (!(type instanceof AnnotatableType annotatableType)) {
            return;
        }

        BMap<BString, Object> annotations = annotatableType.getAnnotations();

        if (annotations.isEmpty()) {
            return;
        }

        Pattern fieldAnnotationPattern = Pattern.compile("^\\$field\\$\\.(.+)$");
        Pattern annotationNamePattern = Pattern.compile("([^:]+)$");

        Map<String, List<String>> dependentRequiredMap = new LinkedHashMap<>();
        Map<String, Object> dependentSchemasMap = new LinkedHashMap<>();

        for (BString key : annotations.getKeys()) {
            String annotationIdentifier = key.getValue();
            Matcher fieldMatcher = fieldAnnotationPattern.matcher(annotationIdentifier);

            if (!fieldMatcher.find()) {
                continue;
            }

            String fieldName = fieldMatcher.group(1);
            Object fieldAnnotations = annotations.get(key);

            if (!(fieldAnnotations instanceof BMap<?, ?>)) {
                continue;
            }

            BMap<BString, Object> fieldAnnotationMap = (BMap<BString, Object>) fieldAnnotations;

            for (BString fieldKey : fieldAnnotationMap.getKeys()) {
                String fieldAnnotationIdentifier = fieldKey.getValue();
                Matcher annotationMatcher = annotationNamePattern.matcher(fieldAnnotationIdentifier);
                String annotationName = annotationMatcher.find() ? annotationMatcher.group(1) : fieldAnnotationIdentifier;

                Object annotation = fieldAnnotationMap.get(fieldKey);

                switch (annotationName) {
                    case "DependentRequired":
                        extractDependentRequiredAnnotation(fieldName, annotation, dependentRequiredMap);
                        break;
                    case "DependentSchema":
                        extractDependentSchemaAnnotation(fieldName, annotation, dependentSchemasMap);
                        break;
                    default:
                        break;
                }
            }
        }

        if (!dependentRequiredMap.isEmpty()) {
            keywords.put(DependentRequiredKeyword.keywordName, new DependentRequiredKeyword(dependentRequiredMap));
        }

        if (!dependentSchemasMap.isEmpty()) {
            keywords.put(DependentSchemasKeyword.keywordName, new DependentSchemasKeyword(dependentSchemasMap));
        }
    }

    private void extractDependentRequiredAnnotation(String fieldName, Object annotation,
                                               Map<String, List<String>> dependentRequiredMap) {
        if (!(annotation instanceof BMap<?, ?> annotationMap)) {
            return;
        }

        if (!annotationMap.containsKey(Constants.VALUE)) {
            return;
        }

        Object value = annotationMap.get(Constants.VALUE);
        List<String> dependentFields = new ArrayList<>();

        if (value instanceof BString stringValue) {
            dependentFields.add(stringValue.getValue());
        } else if (value instanceof BArray arrayValue) {
            for (long i = 0; i < arrayValue.size(); i++) {
                Object element = arrayValue.get(i);
                if (element instanceof BString stringElement) {
                    dependentFields.add(stringElement.getValue());
                }
            }
        }

        if (!dependentFields.isEmpty()) {
            dependentRequiredMap.put(fieldName, dependentFields);
        }
    }

    private void extractDependentSchemaAnnotation(String fieldName, Object annotation,
                                               Map<String, Object> dependentSchemasMap) {
        if (!(annotation instanceof BMap<?, ?> annotationMap)) {
            return;
        }

        if (!annotationMap.containsKey(Constants.VALUE)) {
            return;
        }

        Object value = annotationMap.get(Constants.VALUE);
        if (value instanceof BTypedesc typedesc) {
            Object schema = parse(typedesc.getDescribingType());
            if (schema instanceof Schema || schema instanceof Boolean) {
                dependentSchemasMap.put(fieldName, schema);
            }
        }
    }

    public Object parseRecordType(Type type) {
        Type referredType = TypeUtils.getReferredType(type);

        if (!(referredType instanceof RecordType recordType)) {
            return DiagnosticLog.createJsonError("expected record type, got: " + referredType);
        }

        LinkedHashMap<String, Keyword> keywords = new LinkedHashMap<>();

        Set<String> typeNames = new HashSet<>();
        typeNames.add("object");
        keywords.put(TypeKeyword.keywordName, new TypeKeyword(typeNames));

        Map<String, Field> fields = recordType.getFields();
        Type restType = recordType.getRestFieldType();

        if (restType == null && fields.isEmpty()) {
            extractKeywordsFromAnnotations(type, keywords);
            if (keywords.get("propertyNames") == null) {
                keywords.put(PropertyNamesKeyword.keywordName, new PropertyNamesKeyword(false));
            }
            return new Schema(keywords);
        }

        HashMap<String, Object> propertiesMap = new HashMap<>();
        ArrayList<String> requiredFieldNames = new ArrayList<>();

        for (String fieldName : fields.keySet()) {
            Field field = fields.get(fieldName);
            Object fieldSchema = parse(field.getFieldType());
            if (fieldSchema instanceof BError) {
                return fieldSchema;
            }
            propertiesMap.put(fieldName, fieldSchema);

            boolean isOptional = SymbolFlags.isFlagOn(field.getFlags(), SymbolFlags.OPTIONAL);
            if (!isOptional) {
                requiredFieldNames.add(fieldName);
            }
        }

        keywords.put(PropertiesKeyword.keywordName, new PropertiesKeyword(propertiesMap));
        if (!requiredFieldNames.isEmpty()) {
            keywords.put(RequiredKeyword.keywordName, new RequiredKeyword(requiredFieldNames));
        }

        extractKeywordsFromAnnotations(type, keywords);
        extractKeywordsFromFieldAnnotations(type, keywords);
        // rest type is not exactly the additional property keyword
        if (restType != null) {
            // TODO: Unevaluated properties have a rest type of json as well, check that too
            if (!keywords.containsKey(AdditionalPropertiesKeyword.keywordName)) {
                Object restSchema = parse(restType);
                if (restSchema instanceof BError) {
                    return restSchema;
                }
                keywords.put(AdditionalPropertiesKeyword.keywordName, new AdditionalPropertiesKeyword(restSchema));
            }
        }

        return new Schema(keywords);
    }

    public Object parseArrayType(Type type) {
        Type referredType = TypeUtils.getReferredType(type);

        LinkedHashMap<String, Keyword> keywords = new LinkedHashMap<>();

        Set<String> typeNames = new HashSet<>();
        typeNames.add("array");
        keywords.put(TypeKeyword.keywordName, new TypeKeyword(typeNames));

        extractKeywordsFromAnnotations(type, keywords);

        if (referredType.getTag() == TypeTags.ARRAY_TAG) {
            return parseSimpleArray(type, keywords);
        } else if (referredType.getTag() == TypeTags.TUPLE_TAG) {
            return parseTupleType(type, keywords);
        }

        return DiagnosticLog.createJsonError("unsupported array type: " + referredType);
    }

    private Object parseSimpleArray(Type type, LinkedHashMap<String, Keyword> keywords) {
        ArrayType arrayType = (ArrayType) TypeUtils.getReferredType(type);

        Type elementType = arrayType.getElementType();
        Object itemsSchema = parse(elementType);
        if (itemsSchema instanceof BError) {
            return itemsSchema;
        }
        keywords.put(ItemsKeyword.keywordName, new ItemsKeyword(itemsSchema));

        if (arrayType.getSize() != -1) {
            setArraySizeConstraints(keywords, (long) arrayType.getSize(), (long) arrayType.getSize());
        }

        return new Schema(keywords);
    }

    private Object parseTupleType(Type type, LinkedHashMap<String, Keyword> keywords) {
        TupleType tupleType = (TupleType) TypeUtils.getReferredType(type);
        List<Type> tupleTypes = tupleType.getTupleTypes();
        Type restType = tupleType.getRestType();

        if (tupleTypes.isEmpty()) {
            if (restType == null) {
                return DiagnosticLog.createJsonError("cannot create schema for empty tuple");
            }
            return parseTupleWithRest(type, tupleTypes, restType, keywords);
        }

        if (restType != null) {
            return parseTupleWithRest(type, tupleTypes, restType, keywords);
        } else {
            return parseTupleFixedLength(type, tupleTypes, keywords);
        }
    }

    private Object parseTupleWithRest(Type type, List<Type> tupleTypes, Type restType, LinkedHashMap<String, Keyword> keywords) {
        List<Object> prefixSchemas = new ArrayList<>();
        for (Type memberType : tupleTypes) {
            Object memberSchema = parse(memberType);
            if (memberSchema instanceof BError) {
                return memberSchema;
            }
            prefixSchemas.add(memberSchema);
        }

        if (!prefixSchemas.isEmpty()) {
            keywords.put(PrefixItemsKeyword.keywordName, new PrefixItemsKeyword(prefixSchemas));
        }

        Object restSchema = parse(restType);
        if (restSchema instanceof BError) {
            return restSchema;
        }
        keywords.put(ItemsKeyword.keywordName, new ItemsKeyword(restSchema));

        setArraySizeConstraints(keywords, (long) tupleTypes.size(), null);

        return new Schema(keywords);
    }

    private Object parseTupleFixedLength(Type type, List<Type> tupleTypes, LinkedHashMap<String, Keyword> keywords) {
        List<Object> prefixSchemas = new ArrayList<>();
        for (Type memberType : tupleTypes) {
            Object memberSchema = parse(memberType);
            if (memberSchema instanceof BError) {
                return memberSchema;
            }
            prefixSchemas.add(memberSchema);
        }

        keywords.put(PrefixItemsKeyword.keywordName, new PrefixItemsKeyword(prefixSchemas));

        long tupleSize = tupleTypes.size();
        setArraySizeConstraints(keywords, tupleSize, tupleSize);

        return new Schema(keywords);
    }

    private void setArraySizeConstraints(LinkedHashMap<String, Keyword> keywords, Long derivedMin, Long derivedMax) {
        Keyword minKeyword = keywords.get(MinItemsKeyword.keywordName);
        Keyword maxKeyword = keywords.get(MaxItemsKeyword.keywordName);
        Long annotatedMin = minKeyword != null ? (Long) minKeyword.getKeywordValue() : null;
        Long annotatedMax = maxKeyword != null ? (Long) maxKeyword.getKeywordValue() : null;

        Long finalMin = mergeMinConstraints(annotatedMin, derivedMin);
        Long finalMax = mergeMaxConstraints(annotatedMax, derivedMax);

        if (finalMin != null) {
            keywords.put(MinItemsKeyword.keywordName, new MinItemsKeyword(finalMin));
        }
        if (finalMax != null) {
            keywords.put(MaxItemsKeyword.keywordName, new MaxItemsKeyword(finalMax));
        }
    }

    private Long mergeMinConstraints(Long annotated, Long derived) {
        if (annotated == null) return derived;
        if (derived == null) return annotated;
        return Math.max(annotated, derived);
    }

    private Long mergeMaxConstraints(Long annotated, Long derived) {
        if (annotated == null) return derived;
        if (derived == null) return annotated;
        return Math.min(annotated, derived);
    }

    public Object parseBasicType(Type type) {
        Type referredType = TypeUtils.getReferredType(type);
        if (referredType.getTag() == TypeTags.JSON_TAG) {
            return true;
        }
        if (referredType.getTag() == TypeTags.NEVER_TAG) {
            return false;
        }

        TypeKeyword typeKeyword = extractTypeKeyword(new ArrayList<>(List.of(referredType)));
        LinkedHashMap<String, Keyword> keywords = new LinkedHashMap<>();
        if (typeKeyword != null) {
            keywords.put(TypeKeyword.keywordName, typeKeyword);
        }

        extractConstOrEnumKeyword(new ArrayList<Type>(List.of(referredType)), keywords);
        extractKeywordsFromAnnotations(type, keywords);

        return new Schema(keywords);
    }

    private void extractNumericValidationKeywords(BMap<BString, Object> annotation, LinkedHashMap<String, Keyword> keywords) {
        extractDouble(annotation, "minimum").ifPresent(value ->
                keywords.put(MinimumKeyword.keywordName, new MinimumKeyword(value))
        );
        extractDouble(annotation, "maximum").ifPresent(value ->
                keywords.put(MaximumKeyword.keywordName, new MaximumKeyword(value))
        );
        extractDouble(annotation, "exclusiveMinimum").ifPresent(value ->
                keywords.put(ExclusiveMinimumKeyword.keywordName, new ExclusiveMinimumKeyword(value))
        );
        extractDouble(annotation, "exclusiveMaximum").ifPresent(value ->
                keywords.put(ExclusiveMaximumKeyword.keywordName, new ExclusiveMaximumKeyword(value))
        );
        extractDouble(annotation, "multipleOf").ifPresent(value ->
                keywords.put(MultipleOfKeyword.keywordName, new MultipleOfKeyword(value))
        );
    }

    private void extractStringValidationKeywords(BMap<BString, Object> annotation, LinkedHashMap<String, Keyword> keywords) {
        extractLong(annotation, "minLength").ifPresent(value ->
                keywords.put(MinLengthKeyword.keywordName, new MinLengthKeyword(value))
        );
        extractLong(annotation, "maxLength").ifPresent(value ->
                keywords.put(MaxLengthKeyword.keywordName, new MaxLengthKeyword(value))
        );

        BString patternKey = StringUtils.fromString("pattern");
        if (annotation.containsKey(patternKey)) {
            Object value = annotation.get(patternKey);
            if (value instanceof BRegexpValue regExVal) {
                String regexString = regExVal.toString();
                keywords.put(PatternKeyword.keywordName, new PatternKeyword(regexString));
            } else if (value instanceof BString strVal) {
                keywords.put(PatternKeyword.keywordName, new PatternKeyword(strVal.getValue()));
            }
        }
    }

    private void extractArrayValidationKeywords(BMap<BString, Object> annotation, LinkedHashMap<String, Keyword> keywords) {
        extractLong(annotation, "minItems").ifPresent(value ->
                keywords.put(MinItemsKeyword.keywordName, new MinItemsKeyword(value))
        );
        extractLong(annotation, "maxItems").ifPresent(value ->
                keywords.put(MaxItemsKeyword.keywordName, new MaxItemsKeyword(value))
        );
        extractBoolean(annotation, "uniqueItems").ifPresent(value ->
                keywords.put(UniqueItemsKeyword.keywordName, new UniqueItemsKeyword(value))
        );
        extractObject(annotation, "contains").ifPresent(containsConfig -> {
            Long minContains = extractLongFromMap(containsConfig, "minContains").orElse(null);
            Long maxContains = extractLongFromMap(containsConfig, "maxContains").orElse(null);
            if (containsConfig.containsKey("value")) {
                Object value = containsConfig.get("value");
                if (value instanceof BTypedesc typedesc) {
                    Object containsSchema = parse(typedesc.getDescribingType());
                    if (containsSchema instanceof Schema || containsSchema instanceof Boolean) {
                        keywords.put(ContainsKeyword.keywordName, new ContainsKeyword(minContains, maxContains, containsSchema));
                    }
                }
            }
        });
    }

    private void extractObjectValidationKeywords(BMap<BString, Object> annotation,
                                             LinkedHashMap<String, Keyword> keywords) {
        ArrayList<BString> keys = new ArrayList<>(List.of(annotation.getKeys()));

        BString propertyNamesKey = StringUtils.fromString("propertyNames");
        if (keys.contains(propertyNamesKey)) {
            Object propertyNamesAnnotation = annotation.get(propertyNamesKey);
            if (propertyNamesAnnotation instanceof BTypedesc typedesc) {
                Object propertyNamesSchema = parse(typedesc.getDescribingType());
                if (propertyNamesSchema instanceof Schema || propertyNamesSchema instanceof Boolean) {
                    keywords.put(PropertyNamesKeyword.keywordName,
                            new PropertyNamesKeyword(propertyNamesSchema));
                }
            }
        }

        extractLong(annotation, "minProperties").ifPresent(value ->
                keywords.put(MinPropertiesKeyword.keywordName, new MinPropertiesKeyword(value))
        );

        extractLong(annotation, "maxProperties").ifPresent(value ->
                keywords.put(MaxPropertiesKeyword.keywordName, new MaxPropertiesKeyword(value))
        );
    }

    private void extractPatternProperties(BMap<BString, Object> annotation,
                                       LinkedHashMap<String, Keyword> keywords) {
        BString valueKey = StringUtils.fromString("value");
        Object value = annotation.get(valueKey);
        if (value == null) {
            return;
        }

        List<BMap<BString, Object>> elements = new ArrayList<>();
        if (value instanceof BMap) {
            elements.add((BMap<BString, Object>) value);
        } else if (value instanceof BArray array) {
            for (long i = 0; i < array.size(); i++) {
                Object element = array.get(i);
                if (element instanceof BMap) {
                    elements.add((BMap<BString, Object>) element);
                }
            }
        }

        Map<String, Object> patternSchemaMap = new LinkedHashMap<>();
        for (BMap<BString, Object> element : elements) {
            BString patternKey = StringUtils.fromString("pattern");
            BString valueKey2 = StringUtils.fromString("value");

            if (element.containsKey(patternKey) && element.containsKey(valueKey2)) {
                String pattern = element.get(patternKey).toString();
                Object typedesc = element.get(valueKey2);

                if (typedesc instanceof BTypedesc) {
                    Object schema = parse(((BTypedesc) typedesc).getDescribingType());
                    if (schema instanceof Schema || schema instanceof Boolean) {
                        patternSchemaMap.put(pattern, schema);
                    }
                }
            }
        }

        if (!patternSchemaMap.isEmpty()) {
            keywords.put(PatternPropertiesKeyword.keywordName,
                   new PatternPropertiesKeyword(patternSchemaMap));
        }
    }

    private void extractAdditionalProperties(BMap<BString, Object> annotation,
                                          LinkedHashMap<String, Keyword> keywords) {
        BString valueKey = StringUtils.fromString("value");
        if (annotation.containsKey(valueKey)) {
            Object value = annotation.get(valueKey);
            if (value instanceof BTypedesc typedesc) {
                Object additionalPropertiesSchema = parse(typedesc.getDescribingType());
                if (additionalPropertiesSchema instanceof Schema || additionalPropertiesSchema instanceof Boolean) {
                    keywords.put(AdditionalPropertiesKeyword.keywordName,
                               new AdditionalPropertiesKeyword(additionalPropertiesSchema));
                }
            }
        }
    }


    private Optional<Map<String, Object>> extractObject(BMap<BString, Object> annotation, String keyName) {
        BString key = StringUtils.fromString(keyName);

        if (!annotation.containsKey(key)) {
            return Optional.empty();
        }

        Object value = annotation.get(key);

        if (value instanceof BMap<?, ?> bMap) {
            Map<String, Object> mapVal = new HashMap<>();
            for (Entry<?, ?> entry : bMap.entrySet()) {
                if (entry.getKey() instanceof BString bStrKey) {
                    mapVal.put(bStrKey.getValue(), entry.getValue());
                }
            }
            return Optional.of(mapVal);
        }

        return Optional.empty();
    }

    private Optional<Long> extractLong(BMap<BString, Object> annotation, String keyName) {
        BString key = StringUtils.fromString(keyName);

        if (!annotation.containsKey(key)) {
            return Optional.empty();
        }

        Object value = annotation.get(key);

        if (value instanceof Long longVal) {
            return Optional.of(longVal);
        }

        return Optional.empty();
    }

    private Optional<Long> extractLongFromMap(Map<String, Object> map, String keyName) {
        if (!map.containsKey(keyName)) {
            return Optional.empty();
        }

        Object value = map.get(keyName);

        if (value instanceof Long longVal) {
            return Optional.of(longVal);
        }

        return Optional.empty();
    }

    private Optional<Double> extractDouble(BMap<BString, Object> annotation, String keyName) {
        BString key = StringUtils.fromString(keyName);

        if (!annotation.containsKey(key)) {
            return Optional.empty();
        }

        Object value = annotation.get(key);

        if (value instanceof Long longVal) {
            return Optional.of(longVal.doubleValue());
        } else if (value instanceof Double doubleVal) {
            return Optional.of(doubleVal);
        } else if (value instanceof BDecimal decimalVal) {
            return Optional.of(decimalVal.decimalValue().doubleValue());
        }

        return Optional.empty();
    }

    private Optional<Boolean> extractBoolean(BMap<BString, Object> annotation, String keyName) {
        BString key = StringUtils.fromString(keyName);

        if (!annotation.containsKey(key)) {
            return Optional.empty();
        }

        Object value = annotation.get(key);

        if (value instanceof Boolean boolVal) {
            return Optional.of(boolVal);
        }

        return Optional.empty();
    }
}
package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.ItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PrefixItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.*;
import io.ballerina.lib.data.jsondata.utils.Constants;
import io.ballerina.lib.data.jsondata.utils.DiagnosticErrorCode;
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
import io.ballerina.runtime.api.values.BString;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class TypeParser {
    public Object parse(Type type) {
        Type referredType = TypeUtils.getReferredType(type);
        System.out.println("Parsing type: " + referredType + " tag: " + referredType.getTag());
        return switch (referredType.getTag()) {
            case TypeTags.UNION_TAG -> parseUnionType(type);
            case TypeTags.TUPLE_TAG, TypeTags.ARRAY_TAG -> parseArrayType(type);
            case TypeTags.STRING_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG, TypeTags.BOOLEAN_TAG,
                 TypeTags.JSON_TAG, TypeTags.NEVER_TAG, TypeTags.NULL_TAG, TypeTags.FINITE_TYPE_TAG -> parseBasicType(type);
            default -> DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, referredType.getName());
        };
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
        HashMap<String, Keyword> keywords = new HashMap<>();
        extractKeywordsFromAnnotations(referredType, keywords);

        List<Type> memberTypes = unionType.getMemberTypes();
        System.out.println("Parsing union type: " + referredType + ", with members: " + memberTypes);

        ArrayList<Type> basicTypeMembers = new ArrayList<>();
        ArrayList<Type> constValues = new ArrayList<>();
        List<Object> memberSchemas = new ArrayList<>();

        for (Type memberType : memberTypes) {
            if (memberType.getTag() == TypeTags.TYPE_REFERENCED_TYPE_TAG) {
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

        if (!basicTypeMembers.isEmpty()) {
            TypeKeyword typeKeyword = extractTypeKeyword(basicTypeMembers);
            if (typeKeyword != null) {
                keywords.put(TypeKeyword.keywordName, typeKeyword);
            }
        }

        extractConstOrEnumKeyword(constValues, keywords);

        return new Schema(keywords);
    }

    public void extractConstOrEnumKeyword(ArrayList<Type> referredTypes, HashMap<String, Keyword> keywords) {
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

    public void extractKeywordsFromAnnotations(Type referredType, HashMap<String, Keyword> keywords) {
        if (keywords == null) {
            keywords = new HashMap<>();
        }

        if (!(referredType instanceof AnnotatableType annotatableType)) {
            System.out.println("Type is not annotatable: " + referredType);
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
                    break;
                case "DependentSchema":
                    break;
                case "DependentRequired":
                    break;
                case "PatternProperties":
                    break;
                case "AdditionalProperties":
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
                    break;
                case "OneOf":
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

    public Object parseArrayType(Type type) {
        Type referredType = TypeUtils.getReferredType(type);
        HashMap<String, Keyword> keywords = new HashMap<>();

        Set<String> typeNames = new HashSet<>();
        typeNames.add("array");
        keywords.put(TypeKeyword.keywordName, new TypeKeyword(typeNames));

        extractKeywordsFromAnnotations(type, keywords);

        if (referredType.getTag() == TypeTags.ARRAY_TAG) {
            ArrayType arrayType = (ArrayType) referredType;
            Type elementType = arrayType.getElementType();
            Object itemsSchema = parse(elementType);
            if (itemsSchema instanceof BError) {
                return itemsSchema;
            }
            keywords.put(ItemsKeyword.keywordName, new ItemsKeyword(itemsSchema));
        } else if (referredType.getTag() == TypeTags.TUPLE_TAG) {
            TupleType tupleType = (TupleType) referredType;
            List<Type> tupleTypes = tupleType.getTupleTypes();
            System.out.println("Parsing tuple type in parse Array: " + referredType + ", with members: " + tupleTypes);
            Type restType = tupleType.getRestType();

            if (restType != null) {
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
            } else {
                if (tupleTypes.isEmpty()) {
                    return DiagnosticLog.createJsonError("cannot create schema for empty tuple");
                }

                int splitPoint = tupleTypes.size();
                Type lastType = tupleTypes.get(splitPoint - 1);

                for (int i = tupleTypes.size() - 2; i >= 0; i--) {
                    Type currentType = tupleTypes.get(i);
                    if (areTypesCompatible(lastType, currentType)) {
                        splitPoint = i;
                    } else {
                        break;
                    }
                }
                if (splitPoint == 0) {
                    Object itemsSchema = parse(lastType);
                    if (itemsSchema instanceof BError) {
                        return itemsSchema;
                    }
                    keywords.put(ItemsKeyword.keywordName, new ItemsKeyword(itemsSchema));

                    Long derivedMin = (long) tupleTypes.size();
                    Long derivedMax = (long) tupleTypes.size();

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
                } else if (splitPoint < tupleTypes.size()) {
                    List<Object> prefixSchemas = new ArrayList<>();
                    for (int i = 0; i < splitPoint; i++) {
                        System.out.println("Parsing prefix item type: " + tupleTypes.get(i));
                        Object memberSchema = parse(tupleTypes.get(i));
                        if (memberSchema instanceof BError) {
                            return memberSchema;
                        }
                        prefixSchemas.add(memberSchema);
                    }
                    keywords.put(PrefixItemsKeyword.keywordName, new PrefixItemsKeyword(prefixSchemas));
                    Object itemsSchema = parse(tupleTypes.get(splitPoint));
                    if (itemsSchema instanceof BError) {
                        return itemsSchema;
                    }
                    keywords.put(ItemsKeyword.keywordName, new ItemsKeyword(itemsSchema));

                    Long derivedMin = (long) tupleTypes.size();
                    Long derivedMax = (long) tupleTypes.size();

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
                } else {
                    List<Object> prefixSchemas = new ArrayList<>();
                    for (Type memberType : tupleTypes) {
                        System.out.println("Parsing prefix item type when splitprefix: " +", and, " + "size: "+ tupleTypes.size() + memberType);
                        Object memberSchema = parse(memberType);
                        if (memberSchema instanceof BError) {
                            return memberSchema;
                        }
                        prefixSchemas.add(memberSchema);
                    }
                    keywords.put(PrefixItemsKeyword.keywordName, new PrefixItemsKeyword(prefixSchemas));

                    Long derivedMin = (long) tupleTypes.size();
                    Long derivedMax = (long) tupleTypes.size();

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
            }
        }

        return new Schema(keywords);
    }

    private boolean areTypesCompatible(Type type1, Type type2) {
        Type t1 = TypeUtils.getReferredType(type1);
        Type t2 = TypeUtils.getReferredType(type2);
        
//        if (t1.getTag() != t2.getTag()) {
//            return false;
//        }
        
//        if (t1.getName() != null && t2.getName() != null) {
//            System.out.println("Comapring type names: " + t1.getName() + ", " + t2.getName());
//            return t1.getName().equals(t2.getName()) &&
//                   t1.getPackage().getName().equals(t2.getPackage().getName());
//        }
        String t1Name = type1.getName();
        String t2Name = type2.getName();
        if (t1Name != null && t2Name != null) {
            System.out.println("Comparing type names: " + t1Name + ", " + t2Name);
            return t1Name.equals(t2Name) &&  t1.getTag() == t2.getTag();
        }
        return t1.getTag() == t2.getTag();
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
        HashMap<String, Keyword> keywords = new HashMap<>();
        if (typeKeyword != null) {
            keywords.put(TypeKeyword.keywordName, typeKeyword);
        }

        extractConstOrEnumKeyword(new ArrayList<Type>(List.of(referredType)), keywords);
        extractKeywordsFromAnnotations(type, keywords);

        return new Schema(keywords);
    }

    private void extractNumericValidationKeywords(BMap<BString, Object> annotation, HashMap<String, Keyword> keywords) {
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

    private void extractStringValidationKeywords(BMap<BString, Object> annotation, HashMap<String, Keyword> keywords) {
        extractLong(annotation, "minLength").ifPresent(value ->
                keywords.put(MinLengthKeyword.keywordName, new MinLengthKeyword(value))
        );
        extractLong(annotation, "maxLength").ifPresent(value ->
                keywords.put(MaxLengthKeyword.keywordName, new MaxLengthKeyword(value))
        );
        extractString(annotation, "pattern").ifPresent(value ->
                keywords.put(PatternKeyword.keywordName, new PatternKeyword(value))
        );
    }

    private void extractArrayValidationKeywords(BMap<BString, Object> annotation, HashMap<String, Keyword> keywords) {
        extractLong(annotation, "minItems").ifPresent(value ->
                keywords.put(MinItemsKeyword.keywordName, new MinItemsKeyword(value))
        );
        extractLong(annotation, "maxItems").ifPresent(value ->
                keywords.put(MaxItemsKeyword.keywordName, new MaxItemsKeyword(value))
        );
        extractBoolean(annotation, "uniqueItems").ifPresent(value ->
                keywords.put(UniqueItemsKeyword.keywordName, new UniqueItemsKeyword(value))
        );
    }

    private Optional<String> extractString(BMap<BString, Object> annotation, String keyName) {
        BString key = StringUtils.fromString(keyName);

        if (!annotation.containsKey(key)) {
            return Optional.empty();
        }

        Object value = annotation.get(key);

        if (value instanceof BString strVal) {
            return Optional.of(strVal.getValue());
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
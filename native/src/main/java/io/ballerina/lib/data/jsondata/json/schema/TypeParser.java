package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.*;
import io.ballerina.lib.data.jsondata.utils.Constants;
import io.ballerina.lib.data.jsondata.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.creators.ValueCreator;
import io.ballerina.runtime.api.flags.SymbolFlags;
import io.ballerina.runtime.api.types.AnnotatableType;
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
        return switch (referredType.getTag()) {
            case TypeTags.UNION_TAG -> parseUnionType(type);
            case TypeTags.STRING_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG, TypeTags.BOOLEAN_TAG,
                 TypeTags.JSON_TAG, TypeTags.NEVER_TAG, TypeTags.NULL_TAG, TypeTags.FINITE_TYPE_TAG -> parseBasicType(type);
            default -> DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, referredType.getName());
        };
    }

    public TypeKeyword extractTypeKeyword(ArrayList<Type> type) {
        TypeKeyword typeKeyword = null;
        ArrayList<String> typeNames = new ArrayList<>();
        for (Type memberType : type) {
            Type referredType = TypeUtils.getReferredType(memberType);
            switch (referredType.getTag()) {
                case TypeTags.STRING_TAG -> typeNames.add("string");
                case TypeTags.INT_TAG -> typeNames.add("integer");
                case TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG -> typeNames.add("number");
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
        return typeKeyword;
    }

    public Object parseUnionType(Type type) {
        Type referredType = TypeUtils.getReferredType(type);
        if (!(referredType instanceof UnionType unionType)) {
            return null;
        }
        List<Type> memberTypes = unionType.getMemberTypes();
        ArrayList<Type> basicTypeMembers = new ArrayList<>();
        ArrayList<Type> constValues = new ArrayList<>();
        List<Object> memberSchemas = new ArrayList<>();
        HashMap<String, Keyword> keywords = new HashMap<>();

        for (Type memberType : memberTypes) {
            if (memberType.getTag() == TypeTags.TYPE_REFERENCED_TYPE_TAG) {
                Object parsedMember = parse(memberType);
                if (parsedMember instanceof Schema || parsedMember instanceof Boolean) {
                    memberSchemas.add(parsedMember);
                } else {
                    return (BError) parsedMember;
                }
            } else if (
                memberType.getTag() == TypeTags.INTERSECTION_TAG ||
                memberType.getTag() == TypeTags.TUPLE_TAG ||
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
        extractKeywordsFromAnnotations(referredType, keywords);

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
        extractKeywordsFromAnnotations(referredType, keywords);

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
}
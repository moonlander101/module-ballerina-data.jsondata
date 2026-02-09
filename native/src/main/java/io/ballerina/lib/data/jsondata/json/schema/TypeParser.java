package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.*;
import io.ballerina.lib.data.jsondata.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.types.AnnotatableType;
import io.ballerina.runtime.api.types.FiniteType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.types.UnionType;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    public TypeKeyword extractTypeKeyword(Type type) {
        TypeKeyword typeKeyword = null;
        if (type.getTag() == TypeTags.INT_TAG) {
            typeKeyword = new TypeKeyword("integer");
        } else if (type.getTag() == TypeTags.FLOAT_TAG || type.getTag() == TypeTags.DECIMAL_TAG) {
            typeKeyword = new TypeKeyword("number");
        } else if (type.getTag() == TypeTags.STRING_TAG) {
            typeKeyword = new TypeKeyword("string");
        } else if (type.getTag() == TypeTags.BOOLEAN_TAG) {
            typeKeyword = new TypeKeyword("boolean");
        } else if (type.getTag() == TypeTags.JSON_TAG) {
            typeKeyword = new TypeKeyword("json");
        } else if (type.getTag() == TypeTags.NULL_TAG) {
            typeKeyword = new TypeKeyword("null");
        } else if (type.getTag() == TypeTags.NEVER_TAG) {
            typeKeyword = new TypeKeyword("never");
        }
        return typeKeyword;
    }

    public Object parseUnionType(Type type) {
        if (!(type instanceof UnionType unionType)) {
            return null;
        }
        List<Type> memberTypes = unionType.getMemberTypes();

        List<Object> memberSchemas = new ArrayList<>();
        for (Type memberType : memberTypes) {
            if (memberType.getTag() == TypeTags.TYPE_REFERENCED_TYPE_TAG) {
                Object parsedMember = parse(memberType);
                if (parsedMember instanceof Schema || parsedMember instanceof Boolean) {
                    memberSchemas.add(parsedMember);
                } else {
                    return (BError) parsedMember;
                }
            }
        }
        return memberSchemas;
    }

    public Keyword extractConstOrEnumKeyword(Type type) {
        Type referredType = TypeUtils.getReferredType(type);
        if (referredType instanceof FiniteType finiteType) {
            Set<Object> valueSpace = finiteType.getValueSpace();

            if (valueSpace.size() == 1) {
                Object value = valueSpace.iterator().next();
                return new ConstKeyword(value);
            }
        
            if (valueSpace.size() > 1) {
                return new EnumKeyword(valueSpace); 
            }
        }
        return null;
    }

    public HashMap<String, Keyword> extractKeywordsFromTypeAnnotations(Type type, HashMap<String, Keyword> keywords) {
        if (keywords == null) {
            keywords = new HashMap<>();
        }

        if (!(type instanceof AnnotatableType annotatableType)) {
            return keywords;
        }

        BMap<BString, Object> annotations = annotatableType.getAnnotations();

        if (annotations.isEmpty()) {
            return keywords;
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

        return keywords;
    }

    public Object parseBasicType(Type type) {
        Type referredType = TypeUtils.getReferredType(type);
        if (referredType.getTag() == TypeTags.JSON_TAG) {
            return true;
        }
        if (referredType.getTag() == TypeTags.NEVER_TAG) {
            return false;
        }

        TypeKeyword typeKeyword = extractTypeKeyword(referredType);
        HashMap<String, Keyword> keywords = new HashMap<>();
        if (typeKeyword != null) {
            keywords.put(TypeKeyword.keywordName, typeKeyword);
        }

        extractKeywordsFromTypeAnnotations(referredType, keywords);

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
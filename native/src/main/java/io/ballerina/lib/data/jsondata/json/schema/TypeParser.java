package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.*;
import io.ballerina.lib.data.jsondata.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.types.AnnotatableType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.TypeTags;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.HashMap;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TypeParser {
    public Object parse(Type type) {
        Type referredType = TypeUtils.getReferredType(type);

        return switch (referredType.getTag()) {
            case TypeTags.STRING_TAG, TypeTags.INT_TAG, TypeTags.FLOAT_TAG, TypeTags.DECIMAL_TAG, TypeTags.BOOLEAN_TAG,
                 TypeTags.NULL_TAG -> mapAnnotationToSchema(type);
            default -> DiagnosticLog.error(DiagnosticErrorCode.INVALID_TYPE, referredType.getName());
        };
    }

    public TypeKeyword parseReferredType(Type type) {
        TypeKeyword typeKeyword = null;
        if (type.getTag() == TypeTags.INT_TAG) {
            typeKeyword = new TypeKeyword("integer");
        }
        return typeKeyword;
    }

    public Object mapAnnotationToSchema(Type type) {
        if (!(type instanceof AnnotatableType annotatableType)) {
            return DiagnosticLog.createJsonError("No annotations found for type: " + type.getName());
        }

        BMap<BString, Object> annotations = annotatableType.getAnnotations();

        if (annotations.isEmpty()) {
            return DiagnosticLog.createJsonError("No annotations found for type: " + type.getName());
        }

        Type referredType = TypeUtils.getReferredType(type);

        TypeKeyword typeKeyword = parseReferredType(referredType);
        HashMap<String, Keyword> keywords = new HashMap<>();
        if (typeKeyword != null) {
            keywords.put(TypeKeyword.keywordName, typeKeyword);
        }

        Pattern annotationNamePattern = Pattern.compile("([^:]+)$");

        for (BString key : annotations.getKeys()) {
            String annotationIdentifier = key.getValue();
            Matcher matcher = annotationNamePattern.matcher(annotationIdentifier);
            String annotationName = matcher.find() ? matcher.group(1) : annotationIdentifier;

            Object annotation = annotations.get(key);

            switch (annotationName) {
                case "StringConstraints":
                    break;
                case "NumberConstraints":
                    handleNumberConstraints((BMap<BString, Object>) annotation, keywords);
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
                    System.out.println("Unknown annotation: " + annotationName);
            }
        }

        return new Schema(keywords);
    }

    private void handleNumberConstraints(BMap<BString, Object> annotation, HashMap<String, Keyword> keywords) {
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
package io.ballerina.lib.data.jsondata.json.schema;

import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.AdditionalPropertiesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.AllOfKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.AnyOfKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.DependentSchemasKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.ItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.OneOfKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PatternPropertiesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PrefixItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PropertiesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PropertyNamesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.IdKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.ConstKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.ContainsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.DependentRequiredKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.EnumKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.ExclusiveMaximumKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.ExclusiveMinimumKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.MaxItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.MaxLengthKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.MaxPropertiesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.MaximumKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.MinItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.MinLengthKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.MinPropertiesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.MinimumKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.MultipleOfKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.PatternKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.RequiredKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.TypeKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.UniqueItemsKeyword;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SchemaJsonParser {
    public static Object parse(Object json) {
        if (json instanceof BMap<?,?>) {
            return parseMap((BMap<BString, Object>) json);
        } else if (json instanceof Boolean) {
            return json;
        } else {
            return DiagnosticLog.createJsonError("Invalid JSON Schema");
        }
    }

    public static Object parseMap(BMap<BString, Object> json) {
        LinkedHashMap<String, Keyword> keywords = new LinkedHashMap<>();

        for (BString key : json.getKeys()) {
            String keyStr = key.getValue();
            if (keyStr.equals("minContains") || keyStr.equals("maxContains")) {
                continue;
            }
            Object value = json.get(key);
            Object result = extractKeywords(keyStr, value, keywords);
            if (result instanceof BError) {
                return result;
            }
        }
        return new Schema(keywords);
    }

    public static Object extractKeywords(String key, Object value, LinkedHashMap<String, Keyword> keywords) {
        Long minContains = null;
        Long maxContains = null;

        switch (key) {
            case "type" -> {
                if (value instanceof BString typeName) {
                    keywords.put(TypeKeyword.keywordName, new TypeKeyword(typeName.getValue()));
                } else if (value instanceof BArray typeArray) {
                    Set<String> typeNames = new HashSet<>();
                    for (long i = 0; i < typeArray.size(); i++) {
                        Object element = typeArray.get(i);
                        if (element instanceof BString typeElement) {
                            typeNames.add(typeElement.getValue());
                        } else {
                            return DiagnosticLog.createJsonError("Invalid value for 'type' keyword");
                        }
                    }
                    if (!typeNames.isEmpty()) {
                        keywords.put(TypeKeyword.keywordName, new TypeKeyword(typeNames));
                    }
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'type' keyword");
                }
            }
            case "properties" -> {
                if (value instanceof BMap<?, ?>) {
                    Object properties = parseSchemaMap((BMap<BString, Object>) value);
                    if (properties instanceof BError) {
                        return properties;
                    }
                    keywords.put(PropertiesKeyword.keywordName, new PropertiesKeyword((Map<String, Object>) properties));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'properties' keyword");
                }
            }
            case "patternProperties" -> {
                if (value instanceof BMap<?, ?>) {
                    Object patternProperties = parseSchemaMap((BMap<BString, Object>) value);
                    if (patternProperties instanceof BError) {
                        return patternProperties;
                    }
                    keywords.put(PatternPropertiesKeyword.keywordName,
                            new PatternPropertiesKeyword((Map<String, Object>) patternProperties));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'patternProperties' keyword");
                }
            }
            case "additionalProperties" -> {
                if (value instanceof Boolean) {
                    keywords.put(AdditionalPropertiesKeyword.keywordName,
                            new AdditionalPropertiesKeyword(value));
                } else if (value instanceof BMap<?, ?>) {
                    Object additionalSchema = parse(value);
                    if (additionalSchema instanceof BError) {
                        return additionalSchema;
                    }
                    keywords.put(AdditionalPropertiesKeyword.keywordName,
                            new AdditionalPropertiesKeyword(additionalSchema));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'additionalProperties' keyword");
                }
            }
            case "propertyNames" -> {
                Object propertyNamesSchema = parse(value);
                if (propertyNamesSchema instanceof BError) {
                    return propertyNamesSchema;
                }
                keywords.put(PropertyNamesKeyword.keywordName, new PropertyNamesKeyword(propertyNamesSchema));
            }
            case "items" -> {
                Object itemsSchema = parse(value);
                if (itemsSchema instanceof BError) {
                    return itemsSchema;
                }
                keywords.put(ItemsKeyword.keywordName, new ItemsKeyword(itemsSchema));
            }
            case "prefixItems" -> {
                if (value instanceof BArray) {
                    Object prefixItems = parseSchemaArray((BArray) value);
                    if (prefixItems instanceof BError) {
                        return prefixItems;
                    }
                    keywords.put(PrefixItemsKeyword.keywordName, new PrefixItemsKeyword((List<Object>) prefixItems));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'prefixItems' keyword");
                }
            }
            case "minContains" -> {
                minContains = extractLongValue(value);
                if (minContains == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minContains' keyword");
                }
            }
            case "maxContains" -> {
                maxContains = extractLongValue(value);
                if (maxContains == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maxContains' keyword");
                }
            }
            case "contains" -> {
                Object containsSchema = parse(value);
                if (containsSchema instanceof BError) {
                    return containsSchema;
                }
                keywords.put(ContainsKeyword.keywordName,
                        new ContainsKeyword(minContains, maxContains, containsSchema));
            }
            case "allOf" -> {
                if (value instanceof BArray) {
                    Object allOfSchemas = parseSchemaArray((BArray) value);
                    if (allOfSchemas instanceof BError) {
                        return allOfSchemas;
                    }
                    keywords.put(AllOfKeyword.keywordName, new AllOfKeyword((List<Object>) allOfSchemas));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'allOf' keyword");
                }
            }
            case "anyOf" -> {
                if (value instanceof BArray) {
                    Object anyOfSchemas = parseSchemaArray((BArray) value);
                    if (anyOfSchemas instanceof BError) {
                        return anyOfSchemas;
                    }
                    keywords.put(AnyOfKeyword.keywordName, new AnyOfKeyword((List<Object>) anyOfSchemas));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'anyOf' keyword");
                }
            }
            case "oneOf" -> {
                if (value instanceof BArray) {
                    Object oneOfSchemas = parseSchemaArray((BArray) value);
                    if (oneOfSchemas instanceof BError) {
                        return oneOfSchemas;
                    }
                    keywords.put(OneOfKeyword.keywordName, new OneOfKeyword((List<Object>) oneOfSchemas));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'oneOf' keyword");
                }
            }
            case "required" -> {
                if (value instanceof BArray) {
                    ArrayList<String> requiredFields = new ArrayList<>();
                    BArray requiredArray = (BArray) value;
                    for (long i = 0; i < requiredArray.size(); i++) {
                        Object element = requiredArray.get(i);
                        if (element instanceof BString str) {
                            requiredFields.add(str.getValue());
                        } else {
                            return DiagnosticLog.createJsonError("Invalid value for 'required' keyword");
                        }
                    }
                    keywords.put(RequiredKeyword.keywordName, new RequiredKeyword(requiredFields));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'required' keyword");
                }
            }
            case "dependentRequired" -> {
                if (value instanceof BMap<?, ?>) {
                    Map<String, List<String>> dependentRequired = new LinkedHashMap<>();
                    BMap<BString, Object> dependentMap = (BMap<BString, Object>) value;
                    for (BString dependentKey : dependentMap.getKeys()) {
                        Object dependentValue = dependentMap.get(dependentKey);
                        List<String> requiredFields = new ArrayList<>();
                        if (dependentValue instanceof BString str) {
                            requiredFields.add(str.getValue());
                        } else if (dependentValue instanceof BArray array) {
                            for (long i = 0; i < array.size(); i++) {
                                Object element = array.get(i);
                                if (element instanceof BString elementStr) {
                                    requiredFields.add(elementStr.getValue());
                                } else {
                                    return DiagnosticLog.createJsonError("Invalid value for 'dependentRequired' keyword");
                                }
                            }
                        } else {
                            return DiagnosticLog.createJsonError("Invalid value for 'dependentRequired' keyword");
                        }
                        dependentRequired.put(dependentKey.getValue(), requiredFields);
                    }
                    keywords.put(DependentRequiredKeyword.keywordName,
                            new DependentRequiredKeyword(dependentRequired));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'dependentRequired' keyword");
                }
            }
            case "dependentSchemas" -> {
                if (value instanceof BMap<?, ?>) {
                    Object dependentSchemas = parseSchemaMap((BMap<BString, Object>) value);
                    if (dependentSchemas instanceof BError) {
                        return dependentSchemas;
                    }
                    keywords.put(DependentSchemasKeyword.keywordName,
                            new DependentSchemasKeyword((Map<String, Object>) dependentSchemas));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'dependentSchemas' keyword");
                }
            }
            case "pattern" -> {
                if (value instanceof BString patternValue) {
                    keywords.put(PatternKeyword.keywordName, new PatternKeyword(patternValue.getValue()));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'pattern' keyword");
                }
            }
            case "minLength" -> {
                Long minLength = extractLongValue(value);
                if (minLength == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minLength' keyword");
                }
                keywords.put(MinLengthKeyword.keywordName, new MinLengthKeyword(minLength));
            }
            case "maxLength" -> {
                Long maxLength = extractLongValue(value);
                if (maxLength == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maxLength' keyword");
                }
                keywords.put(MaxLengthKeyword.keywordName, new MaxLengthKeyword(maxLength));
            }
            case "minimum" -> {
                Double minimum = extractDoubleValue(value);
                if (minimum == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minimum' keyword");
                }
                keywords.put(MinimumKeyword.keywordName, new MinimumKeyword(minimum));
            }
            case "maximum" -> {
                Double maximum = extractDoubleValue(value);
                if (maximum == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maximum' keyword");
                }
                keywords.put(MaximumKeyword.keywordName, new MaximumKeyword(maximum));
            }
            case "exclusiveMinimum" -> {
                Double exclusiveMinimum = extractDoubleValue(value);
                if (exclusiveMinimum == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'exclusiveMinimum' keyword");
                }
                keywords.put(ExclusiveMinimumKeyword.keywordName,
                        new ExclusiveMinimumKeyword(exclusiveMinimum));
            }
            case "exclusiveMaximum" -> {
                Double exclusiveMaximum = extractDoubleValue(value);
                if (exclusiveMaximum == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'exclusiveMaximum' keyword");
                }
                keywords.put(ExclusiveMaximumKeyword.keywordName,
                        new ExclusiveMaximumKeyword(exclusiveMaximum));
            }
            case "multipleOf" -> {
                Double multipleOf = extractDoubleValue(value);
                if (multipleOf == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'multipleOf' keyword");
                }
                keywords.put(MultipleOfKeyword.keywordName, new MultipleOfKeyword(multipleOf));
            }
            case "minItems" -> {
                Long minItems = extractLongValue(value);
                if (minItems == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minItems' keyword");
                }
                keywords.put(MinItemsKeyword.keywordName, new MinItemsKeyword(minItems));
            }
            case "maxItems" -> {
                Long maxItems = extractLongValue(value);
                if (maxItems == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maxItems' keyword");
                }
                keywords.put(MaxItemsKeyword.keywordName, new MaxItemsKeyword(maxItems));
            }
            case "uniqueItems" -> {
                if (!(value instanceof Boolean)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'uniqueItems' keyword");
                }
                keywords.put(UniqueItemsKeyword.keywordName, new UniqueItemsKeyword((Boolean) value));
            }
            case "minProperties" -> {
                Long minProperties = extractLongValue(value);
                if (minProperties == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minProperties' keyword");
                }
                keywords.put(MinPropertiesKeyword.keywordName, new MinPropertiesKeyword(minProperties));
            }
            case "maxProperties" -> {
                Long maxProperties = extractLongValue(value);
                if (maxProperties == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maxProperties' keyword");
                }
                keywords.put(MaxPropertiesKeyword.keywordName, new MaxPropertiesKeyword(maxProperties));
            }
            case "enum" -> {
                if (value instanceof BArray array) {
                    Set<Object> enumValues = new HashSet<>();
                    for (long i = 0; i < array.size(); i++) {
                        enumValues.add(array.get(i));
                    }
                    keywords.put(EnumKeyword.keywordName, new EnumKeyword(enumValues));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'enum' keyword");
                }
            }
            case "const" -> keywords.put(ConstKeyword.keywordName, new ConstKeyword(value));
            case "$id" -> {
                if (value instanceof BString) {
                    keywords.put(IdKeyword.keywordName, new IdKeyword(value));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for '$id' keyword");
                }
            }
            default -> {
                // For simplicity, we ignore unrecognized keywords in this example.
            }
        }
        return null;
    }

    private static Object parseSchemaMap(BMap<BString, Object> schemaMap) {
        Map<String, Object> parsedSchemas = new LinkedHashMap<>();
        for (BString key : schemaMap.getKeys()) {
            Object value = schemaMap.get(key);
            Object parsed = parse(value);
            if (parsed instanceof BError) {
                return parsed;
            }
            parsedSchemas.put(key.getValue(), parsed);
        }
        return parsedSchemas;
    }

    private static Object parseSchemaArray(BArray schemas) {
        List<Object> parsedSchemas = new ArrayList<>();
        for (long i = 0; i < schemas.size(); i++) {
            Object value = schemas.get(i);
            Object parsed = parse(value);
            if (parsed instanceof BError) {
                return parsed;
            }
            parsedSchemas.add(parsed);
        }
        return parsedSchemas;
    }

    private static Long extractLongValue(Object value) {
        if (value instanceof Long longValue) {
            return longValue;
        }
        return null;
    }

    private static Double extractDoubleValue(Object value) {
        if (value instanceof Long longValue) {
            return longValue.doubleValue();
        }
        if (value instanceof Double doubleValue) {
            return doubleValue;
        }
        if (value instanceof BDecimal decimalValue) {
            return decimalValue.decimalValue().doubleValue();
        }
        return null;
    }
}

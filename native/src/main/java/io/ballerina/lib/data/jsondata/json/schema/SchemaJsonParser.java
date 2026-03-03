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
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.AnchorKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.IdKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.RefKeyword;
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
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


public class SchemaJsonParser {
    private static final String MOCK_ROOT_URI = "urn:jsonschema:root";

    private final Stack<String> scopeStack = new Stack<>();
    private final SchemaRegistry registry;

    public SchemaJsonParser(SchemaRegistry registry) {
        this.registry = registry;
    }

    public Object parse(Object json) {
        if (json instanceof BMap<?, ?>) {
            return parseSchema((BMap<BString, Object>) json);
        } else if (json instanceof Boolean) {
            return json;
        } else {
            return DiagnosticLog.createJsonError("Invalid JSON Schema: expected object or boolean");
        }
    }

    private Object parseSchema(BMap<BString, Object> json) {
        LinkedHashMap<String, Keyword> keywords = new LinkedHashMap<>();
        boolean scopePushed = false;
        String resolvedId = null;
        String anchorName = null;

        // Pre-scan for minContains/maxContains — needed before we hit "contains"
        Long minContains = extractLong(json, "minContains");
        Long maxContains = extractLong(json, "maxContains");

        BString idKey = io.ballerina.runtime.api.utils.StringUtils.fromString("$id");
        if (json.containsKey(idKey)) {
            Object idValue = json.get(idKey);
            if (!(idValue instanceof BString)) {
                return DiagnosticLog.createJsonError("Invalid value for '$id': expected string");
            }
            String idStr = ((BString) idValue).getValue();
            String base = scopeStack.isEmpty() ? MOCK_ROOT_URI : scopeStack.peek();
            resolvedId = SchemaRegistry.resolveURI(base, idStr);
            scopeStack.push(resolvedId);
            scopePushed = true;
            keywords.put(IdKeyword.keywordName, new IdKeyword(idValue));
        }

        BString anchorKey = io.ballerina.runtime.api.utils.StringUtils.fromString("$anchor");
        if (json.containsKey(anchorKey)) {
            Object anchorValue = json.get(anchorKey);
            if (!(anchorValue instanceof BString)) {
                return DiagnosticLog.createJsonError("Invalid value for '$anchor': expected string");
            }
            anchorName = ((BString) anchorValue).getValue();
            if (!isValidAnchorName(anchorName)) {
                return DiagnosticLog.createJsonError("Invalid $anchor value: must match pattern ^[A-Za-z_][A-Za-z0-9_.-]*$");
            }
            keywords.put(AnchorKeyword.keywordName, new AnchorKeyword(anchorValue));
        }

        try {
            for (BString bKey : json.getKeys()) {
                String key = bKey.getValue();
                Object value = json.get(bKey);

                switch (key) {
                    case "$defs" -> {
                        if (!(value instanceof BMap<?, ?>)) {
                            return DiagnosticLog.createJsonError("Invalid value for '$defs': expected object");
                        }
                        BMap<BString, Object> defs = (BMap<BString, Object>) value;
                        for (BString defName : defs.getKeys()) {
                            Object defRaw = defs.get(defName);
                            Object defParsed = parse(defRaw);
                            if (defParsed instanceof BError) {
                                return defParsed;
                            }
                            // Register under  <baseUri>#/$defs/<name>
                            String currentBase = scopeStack.isEmpty() ? MOCK_ROOT_URI : scopeStack.peek();
                            String fragment = "#/$defs/" + defName.getValue();
                            String defUri = SchemaRegistry.resolveURI(currentBase, fragment);
                            try {
                                registry.put(URI.create(defUri), defParsed);
                            } catch (IllegalArgumentException ignored) {
                                // Malformed URI — skip registration; $ref to it will fail at eval time
                            }
                        }
                    }

                    case "minContains", "maxContains", "$schema", "$comment", "title",
                            "description", "default", "examples", "readOnly", "writeOnly",
                            "deprecated", "$anchor" -> {
                    }

                    default -> {
                        Object err = extractKeyword(key, value, keywords, minContains, maxContains);
                        if (err instanceof BError) {
                            return err;
                        }
                    }
                }
            }

            Schema schema = new Schema(keywords);

            // Register by $id so that absolute-URI $refs resolve to this schema
            if (resolvedId != null) {
                try {
                    registry.put(URI.create(resolvedId), schema);
                } catch (IllegalArgumentException ignored) {
                    // Malformed $id — skip
                }
            }

            if (anchorName != null) {
                String currentBase = scopeStack.isEmpty() ? MOCK_ROOT_URI : scopeStack.peek();
                String anchorUriStr = currentBase + "#" + anchorName;
                URI anchorUri;
                try {
                    anchorUri = URI.create(anchorUriStr);
                    System.out.println("Registering anchor URI: " + anchorUri);
                } catch (IllegalArgumentException e) {
                    return DiagnosticLog.createJsonError("Invalid $anchor URI: " + anchorUriStr);
                }

                if (registry.containsKey(anchorUri)) {
                    return DiagnosticLog.createJsonError("Duplicate fragment URI: " + anchorUriStr);
                }
                registry.put(anchorUri, schema);
            }

            return schema;

        } finally {
            if (scopePushed && !scopeStack.isEmpty()) {
                scopeStack.pop();
            }
        }
    }

    private Object extractKeyword(String key, Object value,
                                  LinkedHashMap<String, Keyword> keywords,
                                  Long minContains, Long maxContains) {
        switch (key) {
            case "type" -> {
                if (value instanceof BString typeName) {
                    keywords.put(TypeKeyword.keywordName, new TypeKeyword(typeName.getValue()));
                } else if (value instanceof BArray typeArray) {
                    Set<String> typeNames = new HashSet<>();
                    for (long i = 0; i < typeArray.size(); i++) {
                        Object el = typeArray.get(i);
                        if (el instanceof BString s) {
                            typeNames.add(s.getValue());
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
                if (!(value instanceof BMap<?, ?>)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'properties' keyword");
                }
                Object parsed = parseSchemaMap((BMap<BString, Object>) value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(PropertiesKeyword.keywordName,
                        new PropertiesKeyword((Map<String, Object>) parsed));
            }

            case "patternProperties" -> {
                if (!(value instanceof BMap<?, ?>)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'patternProperties' keyword");
                }
                Object parsed = parseSchemaMap((BMap<BString, Object>) value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(PatternPropertiesKeyword.keywordName,
                        new PatternPropertiesKeyword((Map<String, Object>) parsed));
            }

            case "additionalProperties" -> {
                if (value instanceof Boolean) {
                    keywords.put(AdditionalPropertiesKeyword.keywordName,
                            new AdditionalPropertiesKeyword(value));
                } else if (value instanceof BMap<?, ?>) {
                    Object parsed = parse(value);
                    if (parsed instanceof BError) {
                        return parsed;
                    }
                    keywords.put(AdditionalPropertiesKeyword.keywordName,
                            new AdditionalPropertiesKeyword(parsed));
                } else {
                    return DiagnosticLog.createJsonError("Invalid value for 'additionalProperties' keyword");
                }
            }

            case "propertyNames" -> {
                Object parsed = parse(value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(PropertyNamesKeyword.keywordName, new PropertyNamesKeyword(parsed));
            }

            case "items" -> {
                Object parsed = parse(value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(ItemsKeyword.keywordName, new ItemsKeyword(parsed));
            }

            case "prefixItems" -> {
                if (!(value instanceof BArray)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'prefixItems' keyword");
                }
                Object parsed = parseSchemaArray((BArray) value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(PrefixItemsKeyword.keywordName,
                        new PrefixItemsKeyword((List<Object>) parsed));
            }

            case "contains" -> {
                Object parsed = parse(value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(ContainsKeyword.keywordName,
                        new ContainsKeyword(minContains, maxContains, parsed));
            }

            case "allOf" -> {
                if (!(value instanceof BArray)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'allOf' keyword");
                }
                Object parsed = parseSchemaArray((BArray) value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(AllOfKeyword.keywordName, new AllOfKeyword((List<Object>) parsed));
            }

            case "anyOf" -> {
                if (!(value instanceof BArray)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'anyOf' keyword");
                }
                Object parsed = parseSchemaArray((BArray) value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(AnyOfKeyword.keywordName, new AnyOfKeyword((List<Object>) parsed));
            }

            case "oneOf" -> {
                if (!(value instanceof BArray)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'oneOf' keyword");
                }
                Object parsed = parseSchemaArray((BArray) value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(OneOfKeyword.keywordName, new OneOfKeyword((List<Object>) parsed));
            }

            case "not" -> {
                Object parsed = parse(value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                // NotKeyword would go here when implemented
            }

            case "required" -> {
                if (!(value instanceof BArray)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'required' keyword");
                }
                ArrayList<String> required = new ArrayList<>();
                BArray arr = (BArray) value;
                for (long i = 0; i < arr.size(); i++) {
                    Object el = arr.get(i);
                    if (el instanceof BString s) {
                        required.add(s.getValue());
                    } else {
                        return DiagnosticLog.createJsonError("Invalid value for 'required' keyword");
                    }
                }
                keywords.put(RequiredKeyword.keywordName, new RequiredKeyword(required));
            }

            case "dependentRequired" -> {
                if (!(value instanceof BMap<?, ?>)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'dependentRequired' keyword");
                }
                BMap<BString, Object> depMap = (BMap<BString, Object>) value;
                Map<String, List<String>> depRequired = new LinkedHashMap<>();
                for (BString depKey : depMap.getKeys()) {
                    Object depVal = depMap.get(depKey);
                    List<String> fields = new ArrayList<>();
                    if (depVal instanceof BString s) {
                        fields.add(s.getValue());
                    } else if (depVal instanceof BArray arr) {
                        for (long i = 0; i < arr.size(); i++) {
                            Object el = arr.get(i);
                            if (el instanceof BString s) {
                                fields.add(s.getValue());
                            } else {
                                return DiagnosticLog.createJsonError(
                                        "Invalid value for 'dependentRequired' keyword");
                            }
                        }
                    } else {
                        return DiagnosticLog.createJsonError("Invalid value for 'dependentRequired' keyword");
                    }
                    depRequired.put(depKey.getValue(), fields);
                }
                keywords.put(DependentRequiredKeyword.keywordName,
                        new DependentRequiredKeyword(depRequired));
            }

            case "dependentSchemas" -> {
                if (!(value instanceof BMap<?, ?>)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'dependentSchemas' keyword");
                }
                Object parsed = parseSchemaMap((BMap<BString, Object>) value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(DependentSchemasKeyword.keywordName,
                        new DependentSchemasKeyword((Map<String, Object>) parsed));
            }

            case "pattern" -> {
                if (!(value instanceof BString pv)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'pattern' keyword");
                }
                keywords.put(PatternKeyword.keywordName, new PatternKeyword(pv.getValue()));
            }

            case "minLength" -> {
                Long v = toLong(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minLength' keyword");
                }
                keywords.put(MinLengthKeyword.keywordName, new MinLengthKeyword(v));
            }

            case "maxLength" -> {
                Long v = toLong(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maxLength' keyword");
                }
                keywords.put(MaxLengthKeyword.keywordName, new MaxLengthKeyword(v));
            }

            case "minimum" -> {
                Double v = toDouble(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minimum' keyword");
                }
                keywords.put(MinimumKeyword.keywordName, new MinimumKeyword(v));
            }

            case "maximum" -> {
                Double v = toDouble(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maximum' keyword");
                }
                keywords.put(MaximumKeyword.keywordName, new MaximumKeyword(v));
            }

            case "exclusiveMinimum" -> {
                Double v = toDouble(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'exclusiveMinimum' keyword");
                }
                keywords.put(ExclusiveMinimumKeyword.keywordName, new ExclusiveMinimumKeyword(v));
            }

            case "exclusiveMaximum" -> {
                Double v = toDouble(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'exclusiveMaximum' keyword");
                }
                keywords.put(ExclusiveMaximumKeyword.keywordName, new ExclusiveMaximumKeyword(v));
            }

            case "multipleOf" -> {
                Double v = toDouble(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'multipleOf' keyword");
                }
                keywords.put(MultipleOfKeyword.keywordName, new MultipleOfKeyword(v));
            }

            case "minItems" -> {
                Long v = toLong(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minItems' keyword");
                }
                keywords.put(MinItemsKeyword.keywordName, new MinItemsKeyword(v));
            }

            case "maxItems" -> {
                Long v = toLong(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maxItems' keyword");
                }
                keywords.put(MaxItemsKeyword.keywordName, new MaxItemsKeyword(v));
            }

            case "uniqueItems" -> {
                if (!(value instanceof Boolean)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'uniqueItems' keyword");
                }
                keywords.put(UniqueItemsKeyword.keywordName, new UniqueItemsKeyword((Boolean) value));
            }

            case "minProperties" -> {
                Long v = toLong(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minProperties' keyword");
                }
                keywords.put(MinPropertiesKeyword.keywordName, new MinPropertiesKeyword(v));
            }

            case "maxProperties" -> {
                Long v = toLong(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maxProperties' keyword");
                }
                keywords.put(MaxPropertiesKeyword.keywordName, new MaxPropertiesKeyword(v));
            }

            case "enum" -> {
                if (!(value instanceof BArray arr)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'enum' keyword");
                }
                Set<Object> enumValues = new HashSet<>();
                for (long i = 0; i < arr.size(); i++) {
                    enumValues.add(arr.get(i));
                }
                keywords.put(EnumKeyword.keywordName, new EnumKeyword(enumValues));
            }

            case "const" -> keywords.put(ConstKeyword.keywordName, new ConstKeyword(value));

            case "$ref" -> {
                if (!(value instanceof BString refVal)) {
                    return DiagnosticLog.createJsonError("Invalid value for '$ref' keyword");
                }
                String refStr = refVal.getValue();
                // Resolve against current scope — always yields an absolute URI
                String base = scopeStack.isEmpty() ? MOCK_ROOT_URI : scopeStack.peek();
                String resolved = SchemaRegistry.resolveURI(base, refStr);
                URI refUri;
                try {
                    refUri = URI.create(resolved);
                } catch (IllegalArgumentException e) {
                    return DiagnosticLog.createJsonError("Invalid URI in '$ref': " + resolved);
                }
                keywords.put(RefKeyword.keywordName, new RefKeyword(refUri));
            }

        }
        return null;
    }

    private Object parseSchemaMap(BMap<BString, Object> schemaMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (BString key : schemaMap.getKeys()) {
            Object parsed = parse(schemaMap.get(key));
            if (parsed instanceof BError) {
                return parsed;
            }
            result.put(key.getValue(), parsed);
        }
        return result;
    }

    private Object parseSchemaArray(BArray schemas) {
        List<Object> result = new ArrayList<>();
        for (long i = 0; i < schemas.size(); i++) {
            Object parsed = parse(schemas.get(i));
            if (parsed instanceof BError) {
                return parsed;
            }
            result.add(parsed);
        }
        return result;
    }

    private Long extractLong(BMap<BString, Object> json, String keyName) {
        BString bKey = StringUtils.fromString(keyName);
        if (!json.containsKey(bKey)) {
            return null;
        }
        return toLong(json.get(bKey));
    }

    private static Long toLong(Object value) {
        if (value instanceof Long l) {
            return l;
        }
        return null;
    }

    private static Double toDouble(Object value) {
        if (value instanceof Long l) {
            return l.doubleValue();
        }
        if (value instanceof Double d) {
            return d;
        }
        if (value instanceof BDecimal bd) {
            return bd.decimalValue().doubleValue();
        }
        return null;
    }

    private static boolean isValidAnchorName(String anchor) {
        if (anchor == null || anchor.isEmpty()) {
            return false;
        }
        return anchor.matches("^[A-Za-z_][A-Za-z0-9_.-]*$");
    }
}

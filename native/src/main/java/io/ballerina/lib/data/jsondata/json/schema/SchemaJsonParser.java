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
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.*;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.AnchorKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.DynamicAnchorKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.DynamicRefKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.IdKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.RefKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.metadata.DefaultKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.metadata.DeprecatedKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.metadata.DescriptionKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.metadata.ExamplesKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.metadata.ReadOnlyKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.metadata.TitleKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.metadata.WriteOnlyKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.unevaluated.UnevaluatedItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.unevaluated.UnevaluatedPropertiesKeyword;
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
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.FormatKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.UniqueItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.content.ContentEncodingKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.content.ContentMediaTypeKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.content.ContentSchemaKeyword;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.lib.data.jsondata.utils.SchemaParserUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;


public class SchemaJsonParser {
    private static final String MOCK_ROOT_URI = "http://wso2.com/schema-root";
    private final SchemaRegistry registry;

    private final Stack<String> scopeStack = new Stack<>();
    private static int parseCount = -1;

    public SchemaJsonParser(SchemaRegistry registry) {
        parseCount++;
        this.registry = registry;
    }

    public Object parse(Object json) {
        if (json instanceof BMap<?, ?>) {
            return parseSchema((BMap<BString, Object>) json);
        } else if (json instanceof Boolean) {
            return json;
        } else {
            return DiagnosticLog.createJsonError("Invalid JSON Schema: expected object or boolean, got " + json.getClass().getSimpleName());
        }
    }

    private Object parseSchema(BMap<BString, Object> json) {
        LinkedHashMap<String, Keyword> keywords = new LinkedHashMap<>();
        boolean scopePushed = false;
        String resolvedId = null;
        String anchorName = null;
        String dynamicAnchorName = null;

        Long minContains = SchemaParserUtils.extractInteger(json, "minContains");
        Long maxContains = SchemaParserUtils.extractInteger(json, "maxContains");

        BString idKey = StringUtils.fromString("$id");
        if (json.containsKey(idKey)) {
            Object idValue = json.get(idKey);
            if (!(idValue instanceof BString)) {
                return DiagnosticLog.createJsonError("Invalid value for '$id': expected string");
            }
            String idStr = ((BString) idValue).getValue();
            String base = scopeStack.isEmpty() ? getMockRootURI() : scopeStack.peek();
            resolvedId = SchemaRegistry.resolveURI(base, idStr);
            scopeStack.push(resolvedId);
            scopePushed = true;
            keywords.put(IdKeyword.keywordName, new IdKeyword(idValue));
        }

        BString anchorKey = StringUtils.fromString("$anchor");
        if (json.containsKey(anchorKey)) {
            Object anchorValue = json.get(anchorKey);
            if (!(anchorValue instanceof BString)) {
                return DiagnosticLog.createJsonError("Invalid value for '$anchor': expected string");
            }
            anchorName = ((BString) anchorValue).getValue();
                if (!SchemaParserUtils.isValidAnchorName(anchorName)) {
                    return DiagnosticLog.createJsonError("Invalid $anchor value: must match pattern " + SchemaParserUtils.VALID_ANCHOR_REGEX);
            }
            keywords.put(AnchorKeyword.keywordName, new AnchorKeyword(anchorValue));
        }

        BString dynamicAnchorKey = StringUtils.fromString("$dynamicAnchor");
        if (json.containsKey(dynamicAnchorKey)) {
            Object dynamicAnchorValue = json.get(dynamicAnchorKey);
            if (!(dynamicAnchorValue instanceof BString)) {
                return DiagnosticLog.createJsonError("Invalid value for '$dynamicAnchor': expected string");
            }
            dynamicAnchorName = ((BString) dynamicAnchorValue).getValue();
                if (!SchemaParserUtils.isValidAnchorName(dynamicAnchorName)) {
                    return DiagnosticLog.createJsonError(
                            "Invalid $dynamicAnchor value: must match pattern " + SchemaParserUtils.VALID_ANCHOR_REGEX);
            }
            keywords.put(DynamicAnchorKeyword.keywordName, new DynamicAnchorKeyword(dynamicAnchorName));
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
                        String currentBase = scopeStack.isEmpty() ? getMockRootURI() : scopeStack.peek();
                        for (BString defName : defs.getKeys()) {
                            String defUriStr = "#/$defs/" + defName.getValue();
                            String defUri = SchemaRegistry.resolveURI(currentBase, defUriStr);

                            Object defRaw = defs.get(defName);
                            Object defParsed = parse(defRaw);
                            if (defParsed instanceof BError) {
                                return defParsed;
                            }
                            try {
                                registry.put(URI.create(defUri), defParsed);
                            } catch (IllegalArgumentException ignored) {
                                // Malformed URI — skip registration; $ref to it will fail at eval time
                            }
                        }
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
            // The root case with no id
            if (scopeStack.isEmpty() && resolvedId == null) {
                registry.put(URI.create(getMockRootURI()), schema);
            }

            // Register by $id so that absolute-URI $refs resolve to this schema
            if (resolvedId != null) {
                try {
                    URI idUri = URI.create(resolvedId);
                    if (registry.containsKey(idUri)) {
                        return DiagnosticLog.createJsonError("Duplicate $id: " + resolvedId);
                    }
                    registry.put(idUri, schema);
                } catch (IllegalArgumentException ignored) {
                    // Malformed $id — skip
                }
            }

            if (anchorName != null) {
                String currentBase = scopeStack.isEmpty() ? getMockRootURI() : scopeStack.peek();
                String anchorUriStr = currentBase + "#" + anchorName;
                URI anchorUri;
                try {
                    anchorUri = URI.create(anchorUriStr);
                } catch (IllegalArgumentException e) {
                    return DiagnosticLog.createJsonError("Invalid $anchor URI: " + anchorUriStr);
                }

                if (registry.containsKey(anchorUri)) {
                    return DiagnosticLog.createJsonError("Duplicate fragment URI: " + anchorUriStr);
                }
                registry.put(anchorUri, schema);
            }

            if (dynamicAnchorName != null) {
                String currentBase = scopeStack.isEmpty() ? getMockRootURI() : scopeStack.peek();
                String dynamicAnchorUriStr = currentBase + "#" + dynamicAnchorName;
                URI dynamicAnchorUri;
                try {
                    dynamicAnchorUri = URI.create(dynamicAnchorUriStr);
                } catch (IllegalArgumentException e) {
                    return DiagnosticLog.createJsonError("Invalid $dynamicAnchor URI: " + dynamicAnchorUriStr);
                }

                if (registry.containsKey(dynamicAnchorUri)) {
                    return DiagnosticLog.createJsonError("Duplicate fragment URI: " + dynamicAnchorUriStr);
                }
                registry.put(dynamicAnchorUri, schema);
                registry.registerDynamicAnchor(dynamicAnchorUri);
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
                keywords.put(NotKeyword.keywordName, new NotKeyword(parsed));
            }

            case "if" -> {
                Object parsed = parse(value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(IfKeyword.keywordName, new IfKeyword(parsed));
            }

            case "then" -> {
                Object parsed = parse(value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(ThenKeyword.keywordName, new ThenKeyword(parsed));
            }

            case "else" -> {
                Object parsed = parse(value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(ElseKeyword.keywordName, new ElseKeyword(parsed));
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
                Long v = SchemaParserUtils.toInteger(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minLength' keyword");
                }
                keywords.put(MinLengthKeyword.keywordName, new MinLengthKeyword(v));
            }

            case "maxLength" -> {
                Long v = SchemaParserUtils.toInteger(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maxLength' keyword");
                }
                keywords.put(MaxLengthKeyword.keywordName, new MaxLengthKeyword(v));
            }

            case "format" -> {
                if (!(value instanceof BString fv)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'format' keyword");
                }
                keywords.put(FormatKeyword.keywordName, new FormatKeyword(fv.getValue()));
            }

            case "minimum" -> {
                Double v = SchemaParserUtils.toNumber(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minimum' keyword");
                }
                keywords.put(MinimumKeyword.keywordName, new MinimumKeyword(v));
            }

            case "maximum" -> {
                Double v = SchemaParserUtils.toNumber(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maximum' keyword");
                }
                keywords.put(MaximumKeyword.keywordName, new MaximumKeyword(v));
            }

            case "exclusiveMinimum" -> {
                Double v = SchemaParserUtils.toNumber(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'exclusiveMinimum' keyword");
                }
                keywords.put(ExclusiveMinimumKeyword.keywordName, new ExclusiveMinimumKeyword(v));
            }

            case "exclusiveMaximum" -> {
                Double v = SchemaParserUtils.toNumber(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'exclusiveMaximum' keyword");
                }
                keywords.put(ExclusiveMaximumKeyword.keywordName, new ExclusiveMaximumKeyword(v));
            }

            case "multipleOf" -> {
                Double v = SchemaParserUtils.toNumber(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'multipleOf' keyword");
                }
                keywords.put(MultipleOfKeyword.keywordName, new MultipleOfKeyword(v));
            }

            case "minItems" -> {
                Long v = SchemaParserUtils.toInteger(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minItems' keyword");
                }
                keywords.put(MinItemsKeyword.keywordName, new MinItemsKeyword(v));
            }

            case "maxItems" -> {
                Long v = SchemaParserUtils.toInteger(value);
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
                Long v = SchemaParserUtils.toInteger(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'minProperties' keyword");
                }
                keywords.put(MinPropertiesKeyword.keywordName, new MinPropertiesKeyword(v));
            }

            case "maxProperties" -> {
                Long v = SchemaParserUtils.toInteger(value);
                if (v == null) {
                    return DiagnosticLog.createJsonError("Invalid value for 'maxProperties' keyword");
                }
                keywords.put(MaxPropertiesKeyword.keywordName, new MaxPropertiesKeyword(v));
            }

            case "title" -> {
                if (value instanceof BString title) {
                    keywords.put(TitleKeyword.keywordName, new TitleKeyword(title.getValue()));
                }
            }

            case "description" -> {
                if (value instanceof BString description) {
                    keywords.put(DescriptionKeyword.keywordName, new DescriptionKeyword(description.getValue()));
                }
            }

            case "default" -> {
                keywords.put(DefaultKeyword.keywordName, new DefaultKeyword(value));
            }

            case "examples" -> {
                if (value instanceof BArray) {
                    keywords.put(ExamplesKeyword.keywordName, new ExamplesKeyword(value));
                }
            }

            case "readOnly" -> {
                if (value instanceof Boolean readOnly) {
                    keywords.put(ReadOnlyKeyword.keywordName, new ReadOnlyKeyword(readOnly));
                }
            }

            case "writeOnly" -> {
                if (value instanceof Boolean writeOnly) {
                    keywords.put(WriteOnlyKeyword.keywordName, new WriteOnlyKeyword(writeOnly));
                }
            }

            case "deprecated" -> {
                if (value instanceof Boolean deprecated) {
                    keywords.put(DeprecatedKeyword.keywordName, new DeprecatedKeyword(deprecated));
                }
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
                String base = scopeStack.isEmpty() ? getMockRootURI() : scopeStack.peek();
                String resolved = SchemaRegistry.resolveURI(base, refStr);
                URI refUri;
                try {
                    refUri = URI.create(resolved);
                } catch (IllegalArgumentException e) {
                    return DiagnosticLog.createJsonError("Invalid URI in '$ref': " + resolved);
                }
                keywords.put(RefKeyword.keywordName, new RefKeyword(refUri));
            }

            case "$dynamicRef" -> {
                if (!(value instanceof BString dynamicRefVal)) {
                    return DiagnosticLog.createJsonError("Invalid value for '$dynamicRef' keyword");
                }
                String dynamicRefStr = dynamicRefVal.getValue();
                // Resolve against current scope — always yields an absolute URI
                String base = scopeStack.isEmpty() ? getMockRootURI() : scopeStack.peek();
                String resolved = SchemaRegistry.resolveURI(base, dynamicRefStr);
                URI dynamicRefUri;
                try {
                    dynamicRefUri = URI.create(resolved);
                } catch (IllegalArgumentException e) {
                    return DiagnosticLog.createJsonError("Invalid URI in '$dynamicRef': " + resolved);
                }

                // A JSON Pointer fragment starts with "/"; plain anchor names do not.
                String fragment = dynamicRefUri.getFragment();
                String anchorNameForRef = (fragment != null && !fragment.startsWith("/")) ? fragment : null;
                keywords.put(DynamicRefKeyword.keywordName, new DynamicRefKeyword(dynamicRefUri, anchorNameForRef));
            }

            case "unevaluatedItems" -> {
                Object parsed = parse(value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(UnevaluatedItemsKeyword.keywordName, new UnevaluatedItemsKeyword(parsed));
            }

            case "unevaluatedProperties" -> {
                Object parsed = parse(value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                keywords.put(UnevaluatedPropertiesKeyword.keywordName, new UnevaluatedPropertiesKeyword(parsed));
            }

            case "contentEncoding" -> {
                if (!(value instanceof BString encoding)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'contentEncoding' keyword: expected string");
                }
                keywords.put(ContentEncodingKeyword.keywordName, new ContentEncodingKeyword(encoding.getValue()));
            }

            case "contentMediaType" -> {
                if (!(value instanceof BString mediaType)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'contentMediaType' keyword: expected string");
                }
                keywords.put(ContentMediaTypeKeyword.keywordName, new ContentMediaTypeKeyword(mediaType.getValue()));
            }

            case "contentSchema" -> {
                Object parsed = parse(value);
                if (parsed instanceof BError) {
                    return parsed;
                }
                if (!(parsed instanceof Schema || parsed instanceof Boolean)) {
                    return DiagnosticLog.createJsonError("Invalid value for 'contentSchema' keyword: expected valid schema");
                }
                keywords.put(ContentSchemaKeyword.keywordName, new ContentSchemaKeyword(parsed));
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

    private String getMockRootURI() {
        if (parseCount == 0) {
            return MOCK_ROOT_URI;
        }
        return MOCK_ROOT_URI + parseCount;
    }
}

package io.ballerina.lib.data.jsondata.utils;

import com.networknt.schema.output.OutputUnit;
import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Schema;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.ItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.applicator.PrefixItemsKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.core.IdKeyword;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation.ContainsKeyword;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class SchemaValidatorUtils {
    private static final String ABSOLUTE_URI_REGEX = "^[a-zA-Z][a-zA-Z0-9+.-]*://.*";

    public static String extractRootIdFromJson(Path jsonFilePath) {
        try {
            String content = Files.readString(jsonFilePath);
            return extractRootIdFromJson(content);
        } catch (IOException e) {
            throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_LOADING_FAILED, jsonFilePath.toString());
        }
    }

    public static String extractRootIdFromJson(String content) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonSchema = mapper.readTree(content);

        if (jsonSchema.has("$id")) {
            return jsonSchema.get("$id").asString();
        }
        throw DiagnosticLog.error(DiagnosticErrorCode.MISSING_SCHEMA_ID);
    }

    public static boolean isAbsoluteUri(String id) {
        return id != null && id.matches(ABSOLUTE_URI_REGEX);
    }

    public static void collectErrors(OutputUnit unit, List<String> errorList) {
        if (unit.getErrors() != null && !unit.getErrors().isEmpty()) {
            unit.getErrors().forEach((keyword, message) -> {
                errorList.add(String.format("At %s: [%s] %s",
                        unit.getInstanceLocation(), keyword, message));
            });
        }

        if (unit.getDetails() != null) {
            for (OutputUnit child : unit.getDetails()) {
                collectErrors(child, errorList);
            }
        }
    }

    public static String createErrorMessage(OutputUnit unit) {
        List<String> allErrors = new ArrayList<>();
        SchemaValidatorUtils.collectErrors(unit, allErrors);

        StringBuilder errorMessage = new StringBuilder();
        for (int i = 0; i < allErrors.size(); i++) {
            if (i > 0) {
                errorMessage.append("\n");
            }
            errorMessage.append("- ").append(allErrors.get(i));
        }
        return errorMessage.toString();
    }

    public static URI getRootResourceUri(Object parsedSchema) {
        URI resourceUri = getResourceUri(parsedSchema);
        if (resourceUri != null) {
            return resourceUri;
        }
        return URI.create("http://wso2.com/schema-root");
    }

    public static URI getResourceUri(Object schema) {
        if (!(schema instanceof Schema)) {
            return null;
        }
        Keyword idKeyword = ((Schema) schema).getKeyword(IdKeyword.keywordName);
        if (idKeyword == null) {
            return null;
        }
        Object idValue = idKeyword.getKeywordValue();
        if (!(idValue instanceof URI uri)) {
            return null;
        }
        try {
            return new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null);
        } catch (Exception e) {
            return null;
        }
    }

    public static void createEvaluatedItemsAnnotation(EvaluationContext context) {
        Object existingEvaluatedItems = context.getAnnotation("evaluatedItems");
        Object prefixItemsAnnotation = context.getAnnotation(PrefixItemsKeyword.keywordName);
        Object itemsAnnotation = context.getAnnotation(ItemsKeyword.keywordName);
        Object containsAnnotation = context.getAnnotation(ContainsKeyword.keywordName);

        Object ifEvaluatedItems = context.getAnnotation("ifEvaluatedItems");
        Object thenEvaluatedItems = context.getAnnotation("thenEvaluatedItems");
        Object elseEvaluatedItems = context.getAnnotation("elseEvaluatedItems");

        Object ifResult = context.getAnnotation("if");

        boolean allEvaluated = false;
        if (ifResult instanceof Boolean ifValid) {
            if (ifValid) {
                if (ifEvaluatedItems instanceof Boolean && (Boolean) ifEvaluatedItems) {
                    allEvaluated = true;
                } else if (thenEvaluatedItems instanceof Boolean && (Boolean) thenEvaluatedItems) {
                    allEvaluated = true;
                }
            } else {
                if (elseEvaluatedItems instanceof Boolean && (Boolean) elseEvaluatedItems) {
                    allEvaluated = true;
                }
            }
        }
        if (prefixItemsAnnotation instanceof Boolean prefixItemsBool && prefixItemsBool) {
            allEvaluated = true;
        } else if (itemsAnnotation instanceof Boolean itemsBool && itemsBool) {
            allEvaluated = true;
        } else if (containsAnnotation instanceof Boolean containsBool && containsBool) {
            allEvaluated = true;
        }
        if (existingEvaluatedItems instanceof Boolean && (Boolean) existingEvaluatedItems) {
            allEvaluated = true;
        }
        
        if (allEvaluated) {
            context.setAnnotation("evaluatedItems", true);
            return;
        }
        
        HashSet<Long> evaluatedIndices = new HashSet<>();
        if (existingEvaluatedItems instanceof List<?> existingList) {
            for (Object idx : existingList) {
                if (idx instanceof Long l) {
                    evaluatedIndices.add(l);
                } else if (idx instanceof Integer i) {
                    evaluatedIndices.add(i.longValue());
                }
            }
        }
        if (prefixItemsAnnotation instanceof Long largestIndex) {
            for (long i = 0; i <= largestIndex; i++) {
                evaluatedIndices.add(i);
            }
        }
        if (containsAnnotation instanceof List<?> containsIndices) {
            for (Object idx : containsIndices) {
                if (idx instanceof Long l) {
                    evaluatedIndices.add(l);
                } else if (idx instanceof Integer i) {
                    evaluatedIndices.add(i.longValue());
                }
            }
        }
        if (ifResult instanceof Boolean ifValid) {
            if (ifValid) {
                if (ifEvaluatedItems instanceof List<?> list) {
                    for (Object idx : list) {
                        if (idx instanceof Long l) {
                            evaluatedIndices.add(l);
                        } else if (idx instanceof Integer i) {
                            evaluatedIndices.add(i.longValue());
                        }
                    }
                }
                if (thenEvaluatedItems instanceof List<?> list) {
                    for (Object idx : list) {
                        if (idx instanceof Long l) {
                            evaluatedIndices.add(l);
                        } else if (idx instanceof Integer i) {
                            evaluatedIndices.add(i.longValue());
                        }
                    }
                }
            } else {
                if (elseEvaluatedItems instanceof List<?> list) {
                    for (Object idx : list) {
                        if (idx instanceof Long l) {
                            evaluatedIndices.add(l);
                        } else if (idx instanceof Integer i) {
                            evaluatedIndices.add(i.longValue());
                        }
                    }
                }
            }
        }
        if (!evaluatedIndices.isEmpty()) {
            context.setAnnotation("evaluatedItems", new ArrayList<>(evaluatedIndices));
        }
    }

    public static void createEvaluatedPropertiesAnnotation(EvaluationContext context) {
        Object existingEvaluatedProperties = context.getAnnotation("evaluatedProperties");
        Object propertiesAnnotation = context.getAnnotation("properties");
        Object patternPropertiesAnnotation = context.getAnnotation("patternProperties");
        Object additionalPropertiesAnnotation = context.getAnnotation("additionalProperties");

        Object ifEvaluatedProperties = context.getAnnotation("ifEvaluatedProperties");
        Object thenEvaluatedProperties = context.getAnnotation("thenEvaluatedProperties");
        Object elseEvaluatedProperties = context.getAnnotation("elseEvaluatedProperties");

        Object ifResult = context.getAnnotation("if");

        boolean allEvaluated = false;
        if (ifResult instanceof Boolean ifValid) {
            if (ifValid) {
                if (ifEvaluatedProperties instanceof Boolean && (Boolean) ifEvaluatedProperties) {
                    allEvaluated = true;
                } else if (thenEvaluatedProperties instanceof Boolean && (Boolean) thenEvaluatedProperties) {
                    allEvaluated = true;
                }
            } else {
                if (elseEvaluatedProperties instanceof Boolean && (Boolean) elseEvaluatedProperties) {
                    allEvaluated = true;
                }
            }
        }
        if (propertiesAnnotation instanceof Boolean propertiesBool && propertiesBool) {
            allEvaluated = true;
        } else if (patternPropertiesAnnotation instanceof Boolean patternPropertiesBool && patternPropertiesBool) {
            allEvaluated = true;
        } else if (additionalPropertiesAnnotation instanceof Boolean additionalPropertiesBool && additionalPropertiesBool) {
            allEvaluated = true;
        } else if (existingEvaluatedProperties instanceof Boolean && (Boolean) existingEvaluatedProperties) {
            allEvaluated = true;
        }
        
        if (allEvaluated) {
            context.setAnnotation("evaluatedProperties", true);
            return;
        }
        
        Set<String> evaluatedProperties = new HashSet<>();
        if (existingEvaluatedProperties instanceof Set<?> existingSet) {
            for (Object value : existingSet) {
                if (value instanceof String propertyName) {
                    evaluatedProperties.add(propertyName);
                }
            }
        }
        if (propertiesAnnotation instanceof Set<?> properties) {
            for (Object value : properties) {
                if (value instanceof String propertyName) {
                    evaluatedProperties.add(propertyName);
                }
            }
        }
        if (patternPropertiesAnnotation instanceof Set<?> patternProperties) {
            for (Object value : patternProperties) {
                if (value instanceof String propertyName) {
                    evaluatedProperties.add(propertyName);
                }
            }
        }
        if (additionalPropertiesAnnotation instanceof Set<?> additionalProperties) {
            for (Object value : additionalProperties) {
                if (value instanceof String propertyName) {
                    evaluatedProperties.add(propertyName);
                }
            }
        }
        if (ifResult instanceof Boolean ifValid) {
            if (ifValid) {
                if (ifEvaluatedProperties instanceof Set<?> ifProps) {
                    for (Object value : ifProps) {
                        if (value instanceof String propertyName) {
                            evaluatedProperties.add(propertyName);
                        }
                    }
                }
                if (thenEvaluatedProperties instanceof Set<?> thenProps) {
                    for (Object value : thenProps) {
                        if (value instanceof String propertyName) {
                            evaluatedProperties.add(propertyName);
                        }
                    }
                }
            } else {
                if (elseEvaluatedProperties instanceof Set<?> elseProps) {
                    for (Object value : elseProps) {
                        if (value instanceof String propertyName) {
                            evaluatedProperties.add(propertyName);
                        }
                    }
                }
            }
        }
        if (!evaluatedProperties.isEmpty()) {
            context.setAnnotation("evaluatedProperties", evaluatedProperties);
        }
    }
}

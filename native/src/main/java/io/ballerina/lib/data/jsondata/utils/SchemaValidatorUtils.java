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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SchemaValidatorUtils {
    private final static String ABSOLUTE_URI_REGEX = "^[a-zA-Z][a-zA-Z0-9+.-]*://.*";

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
        if (parsedSchema instanceof Schema schema) {
            Keyword idKeyword = schema.getKeyword(IdKeyword.keywordName);
            if (idKeyword != null) {
                Object idValue = idKeyword.getKeywordValue();
                if (idValue != null) {
                    try {
                        URI full = URI.create(idValue.toString());
                        return new URI(full.getScheme(), full.getSchemeSpecificPart(), null);
                    } catch (Exception ignored) {
                        // fall through to mock root
                    }
                }
            }
        }
        return URI.create("urn:jsonschema:root");
    }

    public static void createEvaluatedItemsAnnotation(EvaluationContext context) {
        Object prefixItemsAnnotation = context.getAnnotation(PrefixItemsKeyword.keywordName);
        Object itemsAnnotation = context.getAnnotation(ItemsKeyword.keywordName);
        Object containsAnnotation = context.getAnnotation(ContainsKeyword.keywordName);
        if (prefixItemsAnnotation instanceof Boolean prefixItemsBool && prefixItemsBool) {
            context.setAnnotation("evaluatedItems", true);
        } else if (itemsAnnotation instanceof Boolean itemsBool && itemsBool) {
            context.setAnnotation("evaluatedItems", true);
        } else if (containsAnnotation instanceof Boolean containsBool && containsBool) {
            context.setAnnotation("evaluatedItems", true);
        } else {
            HashSet<Long> evaluatedIndices = new HashSet<>();
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
            if (!evaluatedIndices.isEmpty()) {
                context.setAnnotation("evaluatedItems", new ArrayList<>(evaluatedIndices));
            }
        }
    }
}

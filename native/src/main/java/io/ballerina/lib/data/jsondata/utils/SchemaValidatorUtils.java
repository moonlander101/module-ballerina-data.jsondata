package io.ballerina.lib.data.jsondata.utils;

import com.networknt.schema.output.OutputUnit;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SchemaValidatorUtils {
    private final static String ABSOLUTE_URI_REGEX = "^(?:https?:\\/\\/|file:\\/\\/|urn:[a-z0-9][a-z0-9-]{0,31}:)[^\\s#]+$";

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
}

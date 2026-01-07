package io.ballerina.lib.data.jsondata.json.schema;

import com.fasterxml.jackson.annotation.JsonValue;
import com.networknt.schema.*;
import com.networknt.schema.Error;
import com.networknt.schema.regex.JoniRegularExpressionFactory;
import io.ballerina.runtime.api.values.BTypedesc;

import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;

import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BString;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class SchemaValidator {
    private final SchemaRegistry schemaRegistry;
    private static SchemaValidator instance = null;
    private SchemaRetrievalUriResolver schemaResolver = null;

    public static SchemaValidator getInstance(String firstFilePath) {
        if (instance == null) {
            instance = new SchemaValidator(firstFilePath);
        }
        return instance;
    }

    private SchemaValidator(String firstFilePath) {
        this.schemaResolver = new SchemaRetrievalUriResolver(firstFilePath);
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                .regularExpressionFactory(JoniRegularExpressionFactory.getInstance())
                .build();
        this.schemaRegistry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaIdResolvers(resolvers ->
                        resolvers.add(schemaResolver)
                    ).schemas(uri -> {
                    try {
                        if (uri.startsWith("file:/")) {
                            return Files.readString(Paths.get(java.net.URI.create(uri)));
                        } else {
                            throw new RuntimeException("Retrieval URI is not a local file: " + uri);
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to load schema: " + uri, e);
                    }
                }).schemaRegistryConfig(config)
        );
    }

    public Object validateAgainstSchema(Object jsonValue, BString schema) {
        String schemaStr = schema.getValue();
        try {
            StringUtils.getJsonString(schemaStr);
            return validateAgainstSchemaString(jsonValue, schema);
        } catch (IllegalArgumentException e) {
            return validateAgainstSchemaFile(jsonValue, schema);
        }
    }

    public Object validateAgainstSchemaFile(Object jsonValue, BString schemaPath) {
        try {
            String inputString = StringUtils.getJsonString(jsonValue);
            String schemaPathStr = schemaPath.getValue();

            String schemaUri = new File(schemaPathStr).toURI().toString();
            Schema schema = this.schemaRegistry.getSchema(SchemaLocation.of(schemaUri));

            List<Error> errors = schema.validate(inputString,
                    InputFormat.JSON, executionContext -> {
                        executionContext.executionConfig(config1 -> config1.formatAssertionsEnabled(true));
                    });

            if (errors.isEmpty()) {
                return null;
            } else {
                throw new Exception("Schema validation failed with " + errors.size() + " error(s).");
            }
        }
        catch (java.io.IOException e) {
            return DiagnosticLog.createJsonError("IO error while reading schema file: " + e.getMessage());
        }
        catch (Exception e) {
            return DiagnosticLog.createJsonError("Schema validation error: " + e.getMessage());
        }
    }

    public Object validateAgainstSchemaString(Object jsonValue, BString schemaString) {
        try {
            String inputString = StringUtils.getJsonString(jsonValue);
            String schemaStr = schemaString.getValue();

            SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                    .regularExpressionFactory(JoniRegularExpressionFactory.getInstance())
                    .build();

            SchemaRegistry schemaRegistry = SchemaRegistry.withDefaultDialect(
                    SpecificationVersion.DRAFT_2020_12,
                    builder -> builder.schemaRegistryConfig(config)
            );

            Schema schema = schemaRegistry.getSchema(schemaStr);

            List<Error> errors = schema.validate(inputString,
                    InputFormat.JSON, executionContext -> {
                        executionContext.executionConfig(config1 -> config1.formatAssertionsEnabled(true));
                    });

            if (errors.isEmpty()) {
                return null;
            } else {
                throw new Exception("Schema validation failed with " + errors.size() + " error(s).");
            }
        }
        catch (Exception e) {
            return DiagnosticLog.createJsonError("Schema validation error: " + e.getMessage());
        }
    }

    public Object validateAgainstBallerinaType(Object jsonValue, BTypedesc ballerinaType) {
        // Implementation for validating against Ballerina type goes here
        return null;
    }
}

/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.data.jsondata.json.schema;

import com.networknt.schema.InputFormat;
import com.networknt.schema.OutputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.output.OutputUnit;
import com.networknt.schema.regex.JoniRegularExpressionFactory;
import io.ballerina.lib.data.jsondata.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SchemaJsonValidator {
    private final SchemaRegistry registry;

    public SchemaJsonValidator(String schema) {
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                .regularExpressionFactory(JoniRegularExpressionFactory.getInstance())
                .build();

        this.registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaRegistryConfig(config)
        );

        this.registry.getSchema(schema);
    }

    public SchemaJsonValidator(String[] schemas) {
        if (schemas == null || schemas.length == 0) {
            throw new IllegalArgumentException("schemas array cannot be null or empty");
        }

        // all schemas must have $id for the multiple schema case
        Map<String, String> schemaMap = buildSchemaMap(schemas);
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                .regularExpressionFactory(JoniRegularExpressionFactory.getInstance())
                .build();

        this.registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder
                        .schemas(schemaMap)
                        .schemaRegistryConfig(config)
        );
    }

    private Map<String, String> buildSchemaMap(String[] schemas) {
        Map<String, String> schemaMap = new HashMap<>();
        
        for (int i = 0; i < schemas.length; i++) {
            String schemaStr = schemas[i];
            
            // Extract $id using simple string parsing (similar to RetrievalUriResolver)
            String id = extractSchemaId(schemaStr);
            
            if (id == null || id.isEmpty()) {
                throw new RuntimeException(
                        "for multiple schemas, please ensure all $id values are URIs with a scheme " +
                        "(e.g., 'http://example.com/schema.json', 'https://example.com/user.json'). "
                );
            }
            
            // Validate that the $id is an absolute URI with a scheme
            if (!isAbsoluteUri(id)) {
                throw new RuntimeException(
                        "found schema with relative $id '" + id + "'. " +
                        "for multiple schemas, please ensure all $id values are URIs with a scheme " +
                        "(e.g., 'http://example.com/schema.json', 'https://example.com/user.json'). "
                );
            }
            
            schemaMap.put(id, schemaStr);
        }
        
        return schemaMap;
    }

    private String extractSchemaId(String schemaContent) {
        int idIndex = schemaContent.indexOf("\"$id\"");
        if (idIndex != -1) {
            int colonIndex = schemaContent.indexOf(":", idIndex);
            int startQuoteIndex = schemaContent.indexOf("\"", colonIndex);
            int endQuoteIndex = schemaContent.indexOf("\"", startQuoteIndex + 1);
            if (startQuoteIndex != -1 && endQuoteIndex != -1) {
                return schemaContent.substring(startQuoteIndex + 1, endQuoteIndex);
            }
        }
        return null;
    }

    private boolean isAbsoluteUri(String id) {
        return id != null && id.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
    }

    public BError validate(Object jsonValue, String schemaString) {
        try {
            String inputString = StringUtils.getJsonString(jsonValue);
            Schema schemaObj = this.registry.getSchema(schemaString);

            OutputUnit result = schemaObj.validate(inputString,
                    InputFormat.JSON, OutputFormat.HIERARCHICAL, executionContext -> {
                        executionContext.executionConfig(config -> config
                                .formatAssertionsEnabled(true)
                                .annotationCollectionEnabled(true)
                                .annotationCollectionFilter(keyword -> true)
                        );
                    });

            if (!result.isValid()) {
                List<String> allErrors = new ArrayList<>();
                collectErrors(result, allErrors);

                StringBuilder errorMessage = new StringBuilder();
                for (int i = 0; i < allErrors.size(); i++) {
                    if (i > 0) {
                        errorMessage.append("\n");
                    }
                    errorMessage.append("- ").append(allErrors.get(i));
                }
                return DiagnosticLog.error(
                        DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED,
                        errorMessage.toString()
                );
            }

            return null;
        } catch (SchemaException e) {
            if (e.getCause() instanceof FileNotFoundException ex) {
                return DiagnosticLog.createJsonError("schema file not found: " + ex.getMessage());
            } else {
                return DiagnosticLog.createJsonError("schema error: " + e.getMessage());
            }
        } catch (Exception e) {
            return DiagnosticLog.createJsonError("schema processing error: " + e.getMessage());
        }
    }

    private void collectErrors(OutputUnit unit, List<String> errorList) {
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
}

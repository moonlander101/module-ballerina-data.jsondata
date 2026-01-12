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
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SchemaRegistryConfig;
import com.networknt.schema.SpecificationVersion;
import com.networknt.schema.output.OutputUnit;
import com.networknt.schema.regex.JoniRegularExpressionFactory;

import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BString;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SchemaFileValidator {
    private final SchemaRegistry schemaRegistry;

    public static SchemaFileValidator getInstance(String firstFilePath) {
        return new SchemaFileValidator(firstFilePath);
    }

    private SchemaFileValidator(String baseFilePath) {
        if (!baseFilePath.endsWith("json")) {
            throw new RuntimeException("The provided filepath is not a JSON Schema file: " + baseFilePath);
        }

        RetrievalUriResolver schemaResolver = new RetrievalUriResolver(baseFilePath);
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                .regularExpressionFactory(JoniRegularExpressionFactory.getInstance())
                .build();
        this.schemaRegistry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder
                    .schemaIdResolvers(resolvers -> resolvers.add(schemaResolver))
                    .schemas(uri -> {
                        try {
                            if (uri.startsWith("file:")) {
                                return Files.readString(Paths.get(java.net.URI.create(uri)));
                            }
                            return null;
                        } catch (IOException e) {
                            throw new RuntimeException("Failed to load schema: " + uri, e);
                        }
                    })
                    .schemaRegistryConfig(config)
        );
    }

    public Object validate(Object jsonValue, BString schema) {
        try {
            String inputString = StringUtils.getJsonString(jsonValue);
            String schemaPathStr = schema.getValue();

            File schemaFile = new File(schemaPathStr).getAbsoluteFile();
            String schemaUri = schemaFile.toURI().normalize().toString();
            Schema schemaObj = this.schemaRegistry.getSchema(SchemaLocation.of(schemaUri));

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

                StringBuilder errorMessage = new StringBuilder("Failed to meet schema requirements, \n");
                for (String err : allErrors) {
                    errorMessage.append("- ").append(err).append("\n");
                }
                throw new RuntimeException(errorMessage.toString());
            }

            return null;
        }
        catch (SchemaException e) {
            return DiagnosticLog.createJsonError("Schema processing error: " + e.getMessage());
        }
        catch (java.lang.RuntimeException e) {
            return DiagnosticLog.createJsonError("Schema validation error: " + e.getMessage());
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



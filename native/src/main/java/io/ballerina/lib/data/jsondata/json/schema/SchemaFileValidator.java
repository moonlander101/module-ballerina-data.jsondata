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
import io.ballerina.lib.data.jsondata.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.lib.data.jsondata.utils.SchemaValidatorUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BString;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SchemaFileValidator {
    private final SchemaRegistry schemaRegistry;
    private static SchemaFileValidator instance;

    public static SchemaFileValidator getInstance(BString firstFilePath) {
        if (instance == null) {
            instance = new SchemaFileValidator(firstFilePath.getValue());
        }
        return instance;
    }

    private SchemaFileValidator(String baseFilePath) {
        if (baseFilePath == null || baseFilePath.isEmpty()) {
            throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_PATH_NULL_OR_EMPTY);
        }
        if (!baseFilePath.endsWith("json")) {
            throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_SCHEMA_FILE_TYPE, baseFilePath);
        }

        Path absolutePath = Paths.get(baseFilePath).toAbsolutePath().normalize();

        if (!Files.exists(absolutePath)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_FILE_NOT_EXISTS, baseFilePath);
        }
        if (Files.isDirectory(absolutePath)) {
            throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_PATH_IS_DIRECTORY, baseFilePath);
        }

        RetrievalUriResolver schemaResolver = new RetrievalUriResolver(baseFilePath);
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                .regularExpressionFactory(JoniRegularExpressionFactory.getInstance())
                .preloadSchema(false)
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
                            throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_LOADING_FAILED, uri);
                        }
                    })
                    .schemaRegistryConfig(config)
        );
    }

    public BError validate(Object jsonValue, BString schema) {
        try {
            String inputString = StringUtils.getJsonString(jsonValue);
            String schemaPathStr = schema.getValue();

            if (!schemaPathStr.endsWith("json")) {
                throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_SCHEMA_FILE_TYPE, schemaPathStr);
            }

            Path absolutePath = Paths.get(schemaPathStr).toAbsolutePath().normalize();
            if (!Files.exists(absolutePath)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_FILE_NOT_EXISTS, schemaPathStr);
            }
            if (Files.isDirectory(absolutePath)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_PATH_IS_DIRECTORY, schemaPathStr);
            }

            String schemaUri = absolutePath.toUri().toString();
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
                String errMessage = SchemaValidatorUtils.createErrorMessage(result);
                return DiagnosticLog.error(
                        DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED,
                        errMessage
                );
            }

            return null;
        } catch (SchemaException e) {
            if (e.getCause() instanceof FileNotFoundException ex) {
                return DiagnosticLog.error(
                        DiagnosticErrorCode.SCHEMA_FILE_NOT_FOUND,
                        ex.getMessage()
                );
            }
            return DiagnosticLog.createJsonError("schema processing error: " + e.getMessage());
        } catch (Exception e) {
            return DiagnosticLog.createJsonError("schema validation error: " + e.getMessage());
        }
    }
}
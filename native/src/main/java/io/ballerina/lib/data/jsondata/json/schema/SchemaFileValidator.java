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

import com.networknt.schema.*;
import com.networknt.schema.Error;
import com.networknt.schema.regex.JoniRegularExpressionFactory;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BString;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class SchemaFileValidator {
    private final SchemaRegistry schemaRegistry;

    public static SchemaFileValidator getInstance(String firstFilePath) {
        return new SchemaFileValidator(firstFilePath);
    }

    private SchemaFileValidator(String baseFilePath) {
        RetrievalUriResolver schemaResolver = new RetrievalUriResolver(baseFilePath);
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

    public Object validate(Object jsonValue, BString schema) {
        try {
            String inputString = StringUtils.getJsonString(jsonValue);
            String schemaPathStr = schema.getValue();

            String schemaUri = new File(schemaPathStr).toURI().toString();
            Schema schemaObj = this.schemaRegistry.getSchema(SchemaLocation.of(schemaUri));

            List<Error> errors = schemaObj.validate(inputString,
                    InputFormat.JSON, executionContext -> {
                        executionContext.executionConfig(config -> config.formatAssertionsEnabled(true));
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
}



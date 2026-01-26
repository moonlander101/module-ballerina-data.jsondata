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
import com.networknt.schema.output.OutputUnit;
import com.networknt.schema.regex.JoniRegularExpressionFactory;
import io.ballerina.lib.data.jsondata.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.lib.data.jsondata.utils.SchemaValidatorUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
    }

    public SchemaJsonValidator(String[] schemas) {
        if (schemas == null || schemas.length == 0) {
            throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMAS_ARRAY_NULL_OR_EMPTY);
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

    public String findRootSchema(String[] schemas) {
        HashMap<String, Integer> idToSchemaMap = new HashMap<>();
        HashMap<String, Boolean> isRoot = new HashMap<>();
        if (schemas == null || schemas.length == 0) {
            throw DiagnosticLog.error(DiagnosticErrorCode.SCHEMAS_ARRAY_NULL_OR_EMPTY);
        }

        ObjectMapper mapper = new ObjectMapper();
        for (int i = 0; i < schemas.length; i++) {
            String schema = schemas[i];
            JsonNode jsonSchema = mapper.readTree(schema);

            String id = jsonSchema.get("$id").asString();
            idToSchemaMap.put(id, i);
            if (!isRoot.containsKey(id)) {
                isRoot.put(id, true);
            }

            List<JsonNode> values = jsonSchema.findValues("$ref");

            for (JsonNode ref : values) {
                String refVal = ref.asString();
                String absoluteRef = AbsoluteIri.resolve(id, refVal);
                isRoot.put(absoluteRef, false);
            }
        }

        int schemaCount = 0;
        String rootId = "";
        for (Map.Entry<String, Boolean> entry : isRoot.entrySet()) {
            if (entry.getValue() == true) {
                schemaCount += 1;
                rootId = entry.getKey();
            }
        }
        if (schemaCount != 1) {
            throw DiagnosticLog.error(DiagnosticErrorCode.MULTIPLE_ROOT_SCHEMAS);
        }
        return schemas[idToSchemaMap.get(rootId)];
    }

    private Map<String, String> buildSchemaMap(String[] schemas) {
        Map<String, String> schemaMap = new HashMap<>();

        for (String schemaStr : schemas) {
            String id = SchemaValidatorUtils.extractRootIdFromJson(schemaStr);
            if (!SchemaValidatorUtils.isAbsoluteUri(id)) {
                throw DiagnosticLog.error(DiagnosticErrorCode.RELATIVE_SCHEMA_ID, id);
            }

            schemaMap.put(id, schemaStr);
        }
        
        return schemaMap;
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
                String errorMessage = SchemaValidatorUtils.createErrorMessage(result);
                return DiagnosticLog.error(
                        DiagnosticErrorCode.SCHEMA_VALIDATION_FAILED,
                        errorMessage
                );
            }

            return null;
        } catch (SchemaException e) {
            if (e.getCause() instanceof FileNotFoundException ex) {
                return DiagnosticLog.error(DiagnosticErrorCode.SCHEMA_FILE_NOT_EXISTS, ex.getMessage());
            } else {
                return DiagnosticLog.createJsonError("schema error: " + e.getMessage());
            }
        } catch (Exception e) {
            return DiagnosticLog.createJsonError("schema processing error: " + e.getMessage());
        }
    }
}

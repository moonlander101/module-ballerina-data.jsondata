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
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BString;

import java.util.ArrayList;
import java.util.List;

public class SchemaStringValidator {
    private final SchemaRegistry registry;

    public static SchemaStringValidator getInstance() {
        return new SchemaStringValidator();
    }

    private SchemaStringValidator() {
        SchemaRegistryConfig config = SchemaRegistryConfig.builder()
                .regularExpressionFactory(JoniRegularExpressionFactory.getInstance())
                .build();

        this.registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12,
                builder -> builder.schemaRegistryConfig(config)
        );
    }


    public Object validate(Object jsonValue, BString schemaString) {
        try {
            String inputString = StringUtils.getJsonString(jsonValue);
            String schemaStr = schemaString.getValue();

            Schema schema = this.registry.getSchema(schemaStr);

            OutputUnit result = schema.validate(inputString,
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
        }
        catch (Exception e) {
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

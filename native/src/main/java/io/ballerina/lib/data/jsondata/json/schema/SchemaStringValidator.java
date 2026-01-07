package io.ballerina.lib.data.jsondata.json.schema;

import com.networknt.schema.*;
import com.networknt.schema.Error;
import com.networknt.schema.regex.JoniRegularExpressionFactory;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BString;

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
}

// Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/test;
import ballerina/io;
import ballerina/file;

type ValidationTestCase record {|
    json schema;
    json[] valid;
    json[] invalid;
|};

const testFiles = [
    "validation/const.json",
    "validation/dependentRequired.json",
    "validation/enum_complex.json",
    "validation/enum_primitive.json",
    "validation/exclusiveMaximum.json",
    "validation/exclusiveMinimum.json",
    "validation/maxContains.json",
    "validation/maxItems.json",
    "validation/maxLength.json",
    "validation/maxProperties.json",
    "validation/maximum.json",
    "validation/minContains.json",
    "validation/minItems.json",
    "validation/minLength.json",
    "validation/minProperties.json",
    "validation/minimum.json",
    "validation/multipleOf.json",
    "validation/pattern.json",
    "validation/required.json",
    "validation/uniqueItems.json",
    "validation/type_array.json",
    "validation/type_array_of_types.json",
    "validation/type_boolean.json",
    "validation/type_integer.json",
    "validation/type_null.json",
    "validation/type_number.json",
    "validation/type_object.json",
    "validation/type_string.json",
    "validation/applicator/array_contains.json",
    "validation/applicator/array_items.json",
    "validation/applicator/array_prefixItems.json",
    "validation/applicator/conditional_conditional.json",
    "validation/applicator/conditional_dependentSchemas.json",
    "validation/applicator/logic_allOf.json",
    "validation/applicator/logic_anyOf.json",
    "validation/applicator/logic_not.json",
    "validation/applicator/logic_oneOf.json",
    "validation/applicator/object_additionalProperties.json",
    "validation/applicator/object_patternProperties.json",
    "validation/applicator/object_properties.json",
    "validation/applicator/object_propertyNames.json",
    "validation/applicator/unevaluated_unevaluatedItems.json",
    "validation/applicator/unevaluated_unevaluatedProperties.json",
    "validation/format/dates_date-time.json",
    "validation/format/dates_date.json",
    "validation/format/dates_duration.json",
    "validation/format/dates_time.json",
    "validation/format/network_email.json",
    "validation/format/network_hostname.json",
    "validation/format/network_ipv4.json",
    "validation/format/network_ipv6.json",
    "validation/format/other_json-pointer.json",
    "validation/format/other_relative-json-pointer.json",
    "validation/format/uri_uri-reference.json",
    "validation/format/uri_uri.json",
    "validation/format/uri_uuid.json"
];

function dataProviderForSchemaValidation() returns [json, json, string, boolean, string][] {
    [json, json, string, boolean, string][] testData = [];
    
    foreach string testFile in testFiles {
        json|error testCaseJson = getJsonSchemaTestContentFromFile(testFile);
        if testCaseJson is error {
            panic error(string `Failed to load test file: ${testFile}`, testCaseJson);
        }
        
        ValidationTestCase|error testCase = check testCaseJson.cloneWithType(ValidationTestCase);
        if testCase is error {
            panic error(string `Invalid test case structure in file: ${testFile}`, testCase);
        }
        
        foreach int i in 0 ..< testCase.valid.length() {
            testData.push([
                testCase.valid[i],
                testCase.schema,
                testFile,
                true,
                string `${testFile}: valid[${i}]`
            ]);
        }
        
        foreach int i in 0 ..< testCase.invalid.length() {
            testData.push([
                testCase.invalid[i],
                testCase.schema,
                testFile,
                false,
                string `${testFile}: invalid[${i}]`
            ]);
        }
    } on fail var e {
    	panic error(e.message());
    }
    
    return testData;
}
@test:Config {
    dataProvider: dataProviderForSchemaValidation
}
isolated function testSchemaAsJsonValidation(json inputData, json schema, string schemaPath, boolean shouldPass, string testCase) {
    Error? result = validate(inputData, schema);
    if shouldPass {
        test:assertTrue(result is (), testCase + ": Valid data should pass schema validation");
    } else {
        test:assertTrue(result is Error, testCase + ": Invalid data should fail schema validation");
    }
}

@test:Config {
    dataProvider: dataProviderForSchemaValidation
}

isolated function testSchemaAsFilePathValidation(json inputData, json schema, string schemaPath, boolean shouldPass, string testCase) returns error? {
    string tempDir = check file:createTempDir(prefix = "schema_test_");
    string:RegExp separator = re `/`;
    string safeFileName = separator.replaceAll(schemaPath, "_");
    string tempSchemaPath = check file:joinPath(tempDir, string `temp_${safeFileName}`);

    check io:fileWriteJson(tempSchemaPath, schema);
    string absolutePath = check file:getAbsolutePath(tempSchemaPath);
    var result = validate(inputData, absolutePath);

    error? deleteFileResult = file:remove(tempSchemaPath);
    if deleteFileResult is error {
        io:println("Warning: Failed to delete temp file: ", tempSchemaPath);
    }

    error? deleteDirResult = file:remove(tempDir, file:RECURSIVE);
    if deleteDirResult is error {
        io:println("Warning: Failed to delete temp directory: ", tempDir);
    }

    if shouldPass {
        test:assertTrue(result is (), testCase + ": Valid data should pass");
    } else {
        test:assertTrue(result is error, testCase + ": Invalid data should fail");
    }
}
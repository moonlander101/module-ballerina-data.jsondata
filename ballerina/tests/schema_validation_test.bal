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

type JsonSchemaTest record {|
    string description;
    boolean|map<json> schema;
    JsonSchemaTestItem[] tests;
    json...;
|};

type JsonSchemaTestItem record {|
    string description;
    json data;
    boolean valid;
    json...;
|};

const testFiles = [
    "anchor.json",
    "allOf.json",
    "anyOf.json",
    "boolean_schema.json",
    "const.json",
    "contains.json",
    "content.json",
    "default.json",
//    "defs.json",
    "dependentRequired.json",
    "dependentSchemas.json",
    "dynamicRef.json",
    "enum.json",
    "exclusiveMaximum.json",
    "exclusiveMinimum.json",
//    "format.json",
    "if-then-else.json",
    "infinite-loop-detection.json",
    "items.json",
    "maxContains.json",
    "maxItems.json",
    "maxLength.json",
    "maxProperties.json",
    "maximum.json",
    "minContains.json",
    "minItems.json",
    "minLength.json",
    "minProperties.json",
    "minimum.json",
    "multipleOf.json",
    "not.json",
    "oneOf.json"
//    "pattern.json"
//    "patternProperties.json",

//    "prefixItems.json",
//    "properties.json",
//    "propertyNames.json",
//    "ref.json",

//    "refRemote.json",

//    "required.json",
//    "type.json",
//    "unevaluatedItems.json",
//    "unevaluatedProperties.json",
//    "uniqueItems.json",

//    "vocabulary.json"
//    "optional/format/date-time.json",
//    "optional/format/date.json",
//    "optional/format/duration.json",
//    "optional/format/time.json",
//    "optional/format/email.json",
//    "optional/format/hostname.json",
//    "optional/format/ipv4.json",
//    "optional/format/ipv6.json",
//    "optional/format/json-pointer.json",
//    "optional/format/relative-json-pointer.json",
//    "optional/format/uri-reference.json",
//    "optional/format/uri.json",
//    "optional/format/uri-template.json",
//    "optional/format/uuid.json"
];

function dataProviderForSchemaValidation() returns [json, map<json>|boolean, string, boolean, string][] {
    [json, map<json>|boolean, string, boolean, string][] testData = [];

    foreach string testFile in testFiles {
        json|error testCaseJson = getJsonSchemaTestContentFromFile(testFile);
        if testCaseJson is error {
            panic error(string `Failed to load test file: ${testFile}`, testCaseJson);
        }

        if testCaseJson is json[] {
            foreach json testCaseJsonItem in testCaseJson {
                JsonSchemaTest|error testCase = parseAsType(testCaseJsonItem, {}, JsonSchemaTest);
                if testCase is error {
                    panic error(string `Invalid test case structure in file: ${testFile}`, testCase);
                }

                foreach JsonSchemaTestItem testItem in testCase.tests {
                    testData.push([
                        testItem.data,
                        testCase.schema,
                        testFile,
                        testItem.valid,
                        string `${testFile}: ${testCase.description} - ${testItem.description}`
                    ]);
                }
            }
        }
    } on fail var e {
    	panic error(e.message());
    }

    return testData;
}
@test:Config {
    dataProvider: dataProviderForSchemaValidation
}
isolated function testSchemaAsJsonValidation(json inputData, map<json>|boolean schema, string schemaPath, boolean shouldPass, string testCase) {
    Error? result = validate(inputData, schema);
    if shouldPass {
        test:assertTrue(result is (), testCase + ": Valid data should pass schema validation");
    } else {
        test:assertTrue(result is Error, testCase + ": Invalid data should fail schema validation");
    }
}

//@test:Config {
//    dataProvider: dataProviderForSchemaValidation
//}
//
//isolated function testSchemaAsFilePathValidation(json inputData, map<json> schema, string schemaPath, boolean shouldPass, string testCase) returns error? {
//    string tempDir = check file:createTempDir(prefix = "schema_test_");
//    string:RegExp separator = re `/`;
//    string safeFileName = separator.replaceAll(schemaPath, "_");
//    string tempSchemaPath = check file:joinPath(tempDir, string `temp_${safeFileName}`);
//
//    check io:fileWriteJson(tempSchemaPath, schema);
//    string absolutePath = check file:getAbsolutePath(tempSchemaPath);
//    var result = validate(inputData, absolutePath);
//
//    error? deleteFileResult = file:remove(tempSchemaPath);
//    if deleteFileResult is error {
//        io:println("Warning: Failed to delete temp file: ", tempSchemaPath);
//    }
//
//    error? deleteDirResult = file:remove(tempDir, file:RECURSIVE);
//    if deleteDirResult is error {
//        io:println("Warning: Failed to delete temp directory: ", tempDir);
//    }
//
//    if shouldPass {
//        test:assertTrue(result is (), testCase + ": Valid data should pass");
//    } else {
//        test:assertTrue(result is error, testCase + ": Invalid data should fail");
//    }
//}
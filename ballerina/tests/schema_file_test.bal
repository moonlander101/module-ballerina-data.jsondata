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

function dataProviderForSchemaFilePaths() returns [json, string, boolean][] {
    json validProduct = {
        "product_id": "1024",
        "product_name": "Devant",
        "tags": ["integration"]
    };

    json validSubscription = {
        "plan": "enterprise",
        "start_date": "2023-01-01",
        "end_date": "2024-01-01",
        "auto_renew": true,
        "features": ["api_access", "advanced_analytics"]
    };
    
    return [
        [validProduct, "tests/resources/schemas/product.json", true],
        [validSubscription, "tests/resources/schemas/common/subscription.json", true],
        [validProduct, "tests/resources/schemas/nonexistent.json", false],
        [validProduct, "nonexistent/directory/product.json", false],
        [validProduct, "tests/resources/schemas/schema.txt", false],
        [validProduct, "tests/resources/schemas/schema.yaml", false],
        [validProduct, "tests/resources/schemas/common", false]
    ];
}

@test:Config {
    dataProvider: dataProviderForSchemaFilePaths
}
isolated function testSchemaFilePathHandling(json inputData, string schemaPath, boolean shouldPass) {
    Error? result = validate(inputData, schemaPath);
    
    if shouldPass {
        io:println(result);
        test:assertTrue(result is (),  "Valid schema path should not throw error");
    } else {
        test:assertTrue(result is Error, "Invalid schema path should throw error");
    }
}
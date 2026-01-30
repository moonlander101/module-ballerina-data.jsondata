import ballerina/file;
import ballerina/io;
import ballerina/test;
// import ballerina/random;

type TestCase record {
    string description;
    json data;
    boolean valid;
};

type TestSuite record {
    string description;
    json schema;
    TestCase[] tests;
};

@test:Config {
    dataProvider: jsonSchemaFileProvider
}
isolated function testJsonSchemaValidation(string filePath) returns error? {
    json content = check io:fileReadJson(filePath);
    TestSuite[] suites = check content.cloneWithType();

    foreach TestSuite suite in suites {
        json schemaToValidate = suite.schema;
        if schemaToValidate is map<json> {
            if !schemaToValidate.hasKey("$id") {
                schemaToValidate["$id"] = "http://example.com/root.json";
            }
        }

//        io:println(schemaToValidate);

        check runTestsForSchema(suite, schemaToValidate, "JSON Input");

        // string tempFileName = "temp_schema_" + check generateUniqueId() + ".json";
        // check io:fileWriteJson(tempFileName, schemaToValidate);
        // string absPath = check file:getAbsolutePath(tempFileName);

        // var result = trap runTestsForSchema(suite, absPath, "Filepath Input");

        // check file:remove(absPath);
        // check result;
    }
}

isolated function runTestsForSchema(TestSuite suite, json|string schemaInput, string modeName) returns error? {
    foreach TestCase testCase in suite.tests {        
        error? result = validate(testCase.data, schemaInput);
        boolean isValid = (result is ());

        if (isValid != testCase.valid) {
            test:assertFail(
                string `Test Failed!
                Mode: ${modeName}
                Suite: ${suite.description}
                Test: ${testCase.description}
                Expected Valid: ${testCase.valid}
                Actual Result: ${isValid ? "Valid" : "Error"}
                Error Details: ${result is error ? result.message() : "N/A"}`
            );
        }
    }
}

//function generateUniqueId() returns string|error {
//    return (check random:createIntInRange(1000, 99999)).toString();
//}

function jsonSchemaFileProvider() returns string[][]|error {
    string testsDir = "tests/resources/schemas/validation"; 
    string[][] filePaths = [];
    check findJsonFiles(testsDir, filePaths);
    return filePaths;
}

function findJsonFiles(string dir, string[][] filePaths) returns error? {
    file:MetaData[] entries = check file:readDir(dir);

    foreach file:MetaData entry in entries {
        if entry.dir {
            check findJsonFiles(entry.absPath, filePaths);
        } else {
            string path = entry.absPath;
            if path.endsWith(".json") {
                filePaths.push([path]);
            }
        }
    }
}
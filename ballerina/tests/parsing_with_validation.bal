// Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
//
// WSO2 LLC. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied. See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/test;

// ============================================================
// Type Definitions
// ============================================================

// --- Category 1: Basic type constraints on record fields ---

@StringConstraints {
    minLength: 2,
    maxLength: 10
}
public type ParseTest_BoundedString string;

@NumberConstraints {
    minimum: 0.0,
    maximum: 100.0
}
public type ParseTest_BoundedInt int;

public type ParseTest_ConstrainedRecord record {|
    ParseTest_BoundedString name;
    ParseTest_BoundedInt age;
    string email;
|};

// --- Category 2: Top-level basic type constraints ---

@StringConstraints {
    minLength: 1,
    maxLength: 5
}
public type ParseTest_TagString string;

@NumberConstraints {
    minimum: 1.0,
    maximum: 100.0
}
public type ParseTest_BoundedNumber int;

// --- Category 7: Field-level constraints via type aliases ---

@StringConstraints {
    minLength: 2,
    maxLength: 5
}
public type ParseTest_PersonName string;

@NumberConstraints {
    minimum: 0.0,
    maximum: 150.0
}
public type ParseTest_PersonAge int;

public type ParseTest_PersonRecord record {|
    ParseTest_PersonName name;
    ParseTest_PersonAge age;
|};

// --- Category 8: Rest field constraints ---

@StringConstraints {
    minLength: 1
}
public type ParseTest_NonEmptyString string;

public type ParseTest_ConstrainedRestRecord record {|
    string name;
    ParseTest_NonEmptyString...;
|};

// --- Category 9: Nested records with constraints ---

public type ParseTest_AddressConstrained record {|
    ParseTest_BoundedString city;
    ParseTest_BoundedString country;
|};

public type ParseTest_PersonWithAddress record {|
    ParseTest_BoundedString name;
    ParseTest_AddressConstrained address;
|};

// --- Category 10: Union types with @AllOf / @OneOf / @Not ---

public type ParseTest_AllOfRecordA record {|
    string name;
    json...;
|};

public type ParseTest_AllOfRecordB record {|
    int age;
    json...;
|};

@AllOf
public type ParseTest_AllOfTestType ParseTest_AllOfRecordA|ParseTest_AllOfRecordB;

public type ParseTest_NotSchema record {|
    string forbidden;
    json...;
|};

@Not {
    value: ParseTest_NotSchema
}
public type ParseTest_NotTestType record {|
    string name;
    int value?;
    json...;
|};

public type ParseTest_OneOfRecordA record {|
    "A" mode;
    json...;
|};

public type ParseTest_OneOfRecordB record {|
    "B" mode;
    json...;
|};

@OneOf
public type ParseTest_OneOfTestType ParseTest_OneOfRecordA|ParseTest_OneOfRecordB;

// --- Category 14: Readonly intersection with constraints ---

public type ParseTest_ReadonlyConstrained record {|
    ParseTest_BoundedString name;
    int age;
|} & readonly;

// --- Category: Constrained array element in record ---

@NumberConstraints {
    minimum: 0.0
}
public type ParseTest_NonnegativeInt int;

public type ParseTest_ScoresRecord record {|
    string name;
    ParseTest_NonnegativeInt[] scores;
|};

// --- Category: Constrained tuple in record ---

@StringConstraints {
    minLength: 1
}
public type ParseTest_NonEmptyTag string;

public type ParseTest_TagTuple [ParseTest_NonEmptyTag, ParseTest_NonEmptyTag];

public type ParseTest_RecordWithTuple record {|
    string name;
    ParseTest_TagTuple tags;
|};

// --- Category: Nullable field with constraint ---

public type ParseTest_NullableRecord record {|
    ParseTest_BoundedString name;
    ParseTest_BoundedInt? score;
|};

// --- Category: unevaluatedProperties ---

@UnevaluatedProperties {
    value: boolean
}
public type ParseTest_UnevalPropsTest record {|
    string name;
    json...;
|};

// --- Category: unevaluatedItems ---

@ArrayConstraints {
    contains: {value: ParseTest_BoundedInt, minContains: 1, maxContains: 2},
    unevaluatedItems: boolean
}
public type ParseTest_UnevalItemsTest [json...];

// --- Category: exclusiveMinimum / exclusiveMaximum ---

@NumberConstraints {
    exclusiveMinimum: 0.0,
    exclusiveMaximum: 100.0
}
public type ParseTest_ExclusiveRange int;

// --- Category: multipleOf ---

@NumberConstraints {
    multipleOf: 5.0
}
public type ParseTest_MultipleOfFive int;

// --- Category: pattern ---

@StringConstraints {
    pattern: re `^[A-Z][a-z]+$`
}
public type ParseTest_Capitalized string;

// --- Category: maxItems ---

@ArrayConstraints {
    maxItems: 3
}
public type ParseTest_MaxThree [string...];

// --- Category: unevaluatedItems with specific element type ---

@ArrayConstraints {
    unevaluatedItems: boolean
}
public type ParseTest_UnevalItemsString [string...];

// --- Category: unevaluatedItems with tuple ---

@ArrayConstraints {
    unevaluatedItems: boolean
}
public type ParseTest_UnevalItemsTuple [string, int, boolean...];

// ============================================================
// Valid parseAsType cases (should succeed)
// ============================================================

function validParseAsTypeSchemaValidation() returns [json, typedesc<anydata>, anydata][] {
    return [

        // --- Category 1: Field-level basic type constraints ---

        // 1. All fields within bounds
        [
            {"name": "Alice", "age": 25, "email": "a@b.c"},
            ParseTest_ConstrainedRecord,
            {name: "Alice", age: 25, email: "a@b.c"}
        ],
        // 2. Minimum bounds (age=0, name=2 chars)
        [
            {"name": "Jo", "age": 0, "email": "x"},
            ParseTest_ConstrainedRecord,
            {name: "Jo", age: 0, email: "x"}
        ],
        // 3. Maximum bounds (age=100, name=10 chars)
        [
            {"name": "1234567890", "age": 100, "email": "y"},
            ParseTest_ConstrainedRecord,
            {name: "1234567890", age: 100, email: "y"}
        ],

        // --- Category 2: Top-level basic type constraints ---

        // 4. String within bounds
        [
            "hello",
            ParseTest_TagString,
            "hello"
        ],
        // 5. Single char (minLength=1)
        [
            "a",
            ParseTest_TagString,
            "a"
        ],
        // 6. Number within bounds
        [
            50,
            ParseTest_BoundedNumber,
            50
        ],
        // 7. Number at minimum
        [
            1,
            ParseTest_BoundedNumber,
            1
        ],
        // 8. Number at maximum
        [
            100,
            ParseTest_BoundedNumber,
            100
        ],

        // --- Category 3: Array-level constraints ---

        // 9. minItems satisfied (Schema26 needs 10)
        [
            <json>[1, "a", "b", "c", "d", "e", "f", "g", "h", "i"],
            Schema26,
            [1, "a", "b", "c", "d", "e", "f", "g", "h", "i"]
        ],
        // 10. UniqueItems with distinct values (Schema31)
        [
            <json>["apple", "banana", "cherry", "date"],
            Schema31,
            ["apple", "banana", "cherry", "date"]
        ],
        // 11. MinItems with metadata (Schema61)
        [
            <json>["admin", "user"],
            Schema61,
            ["admin", "user"]
        ],
        // 12. Contains string element (Schema62)
        [
            <json>["s", 2, 3],
            Schema62,
            ["s", 2, 3]
        ],
        // 13. UniqueItems + Contains with minContains:0 (Schema32)
        [
            <json>["apple", "banana", "cherry"],
            Schema32,
            ["apple", "banana", "cherry"]
        ],

        // --- Category 4: Array element-level constraints ---

        // 14. String elements meet minLength:5 (Schema21)
        [
            <json>["hello", "world", 25],
            Schema21,
            ["hello", "world", 25]
        ],
        // 15. First element >= 4.0 (Schema24)
        [
            <json>[5, "hello"],
            Schema24,
            [5, "hello"]
        ],

        // --- Category 5: Object-level constraints ---

        // 16. PropertyNames pattern: lowercase + underscore (Schema1)
        [
            {"first_name": "John", "last_name": "Doe", "age": 30},
            Schema1,
            {"first_name": "John", "last_name": "Doe", "age": 30}
        ],
        // 17. PropertyNames maxLength:10 (Schema2)
        [
            {"short": 1, "key": 2},
            Schema2,
            {"short": 1, "key": 2}
        ],
        // 18. PropertyNames minLength:2 (Schema3)
        [
            {"ab": 1, "cd": 2},
            Schema3,
            {"ab": 1, "cd": 2}
        ],
        // 19. MinProperties: 2 satisfied (SchemaMinProps1)
        [
            {"a": 1, "b": 2, "c": 3},
            SchemaMinProps1,
            {"a": 1, "b": 2, "c": 3}
        ],
        // 20. MaxProperties: 3 satisfied (SchemaMaxProps1)
        [
            {"a": 1, "b": 2},
            SchemaMaxProps1,
            {"a": 1, "b": 2}
        ],
        // 21. MinMaxProperties: 2-3 satisfied (SchemaMinMaxProps1)
        [
            {"a": 1, "b": 2},
            SchemaMinMaxProps1,
            {"a": 1, "b": 2}
        ],

        // --- Category 6: PatternProperties & AdditionalProperties ---

        // 22. PatternProperties: S_ prefix -> string, I_ -> int (SchemaPP1)
        [
            {"S_field": "test", "I_field": 42},
            SchemaPP1,
            {"S_field": "test", "I_field": 42}
        ],
        // 23. PatternProperties + AdditionalProperties(never) (SchemaPP5)
        [
            {"allowed": "yes", "S_1": 100},
            SchemaPP5,
            {"allowed": "yes", "S_1": 100}
        ],
        // 24. PatternProperties + AdditionalProperties(boolean) (SchemaPP6)
        [
            {"S_field": "test", "other": true},
            SchemaPP6,
            {"S_field": "test", "other": true}
        ],

        // --- Category 7: Field-level constraints via type aliases ---

        // 25. PersonRecord with valid name and age
        [
            {"name": "John", "age": 30},
            ParseTest_PersonRecord,
            {name: "John", age: 30}
        ],

        // --- Category 8: Rest field constraints ---

        // 26. Rest fields meet minLength:1
        [
            {"name": "test", "key1": "value1", "key2": "value2"},
            ParseTest_ConstrainedRestRecord,
            {name: "test", key1: "value1", key2: "value2"}
        ],

        // --- Category 9: Nested records with constraints ---

        // 27. Nested address fields within bounds
        [
            {"name": "Alice", "address": {"city": "Colombo", "country": "LK"}},
            ParseTest_PersonWithAddress,
            {name: "Alice", address: {city: "Colombo", country: "LK"}}
        ],

        // --- Category 10: Union with @AllOf ---

        // 28. Matches both AllOfRecordA (has name) and AllOfRecordB (has age)
        [
            {"name": "Alice", "age": 30},
            ParseTest_AllOfTestType,
            {"name": "Alice", "age": 30}
        ],

        // --- Category 10: Union with @Not ---

        // 29. Does NOT match NotSchema (no "forbidden" field)
        [
            {"name": "Alice", "value": 42},
            ParseTest_NotTestType,
            {"name": "Alice", value: 42}
        ],

        // --- Category 10: Union with @OneOf ---

        // 30. Matches exactly oneOf (mode="A")
        [
            {"mode": "A"},
            ParseTest_OneOfTestType,
            {"mode": "A"}
        ],
        // 31. Matches exactly oneOf (mode="B")
        [
            {"mode": "B"},
            ParseTest_OneOfTestType,
            {"mode": "B"}
        ],

        // --- Category 11: Required fields ---

        // 32. All required fields present (RequiredSchema1)
        [
            {"name": "John", "email": "john@example.com"},
            RequiredSchema1,
            {name: "John", email: "john@example.com"}
        ],
        // 33. Required + optional present (RequiredSchema1)
        [
            {"name": "Jane", "age": 25, "email": "jane@example.com", "phone": "123"},
            RequiredSchema1,
            {name: "Jane", age: 25, email: "jane@example.com", phone: "123"}
        ],
        // 34. All required present (RequiredSchema2)
        [
            {"name": "Alice", "age": 35, "email": "alice@example.com"},
            RequiredSchema2,
            {name: "Alice", age: 35, email: "alice@example.com"}
        ],

        // --- Category 12: Tuple constraints ---

        // 35. First element >= 4.0 in tuple (Schema24)
        [
            <json>[5, "hello"],
            Schema24,
            [5, "hello"]
        ],
        // 36. Tuple with constrained elements (Schema59)
        [
            <json>["alice", 30, true, false, true],
            Schema59,
            ["alice", 30, true, false, true]
        ],

        // --- Category 13: DependentRequired ---

        // 37. All dependent fields present
        [
            {"name": "John", "id": 123, "age": 30},
            DependentRequiredType,
            {name: "John", id: 123, age: 30}
        ],
        // 38. No dependent fields present (no dependency triggered)
        [
            {},
            DependentRequiredType,
            {}
        ],

        // --- Category 13: DependentSchema ---

        // 39. Age present, name is string (conforms to AgeDependentSchema)
        [
            {"age": 30, "name": "John"},
            DependentSchemaType,
            {name: "John", age: 30}
        ],

        // --- Category 14: Readonly intersection with constraints ---

        // 40. Readonly record with valid constrained fields
        [
            {"name": "Alice", "age": 30},
            ParseTest_ReadonlyConstrained,
            {name: "Alice", age: 30}
        ],

        // --- Category: Constrained array in record ---

        // 41. All scores >= 0
        [
            {"name": "Alice", "scores": [90, 85, 95]},
            ParseTest_ScoresRecord,
            {name: "Alice", scores: [90, 85, 95]}
        ],
        // 42. Empty scores array
        [
            {"name": "Bob", "scores": []},
            ParseTest_ScoresRecord,
            {name: "Bob", scores: []}
        ],

        // --- Category: Constrained tuple in record ---

        // 43. Valid non-empty tags
        [
            {"name": "Alice", "tags": ["java", "ballerina"]},
            ParseTest_RecordWithTuple,
            {name: "Alice", tags: ["java", "ballerina"]}
        ],

        // --- Category: Nullable field with constraint ---

        // 44. Score is present and valid
        [
            {"name": "Alice", "score": 50},
            ParseTest_NullableRecord,
            {name: "Alice", score: 50}
        ],
        // 45. Score is null (nilable)
        [
            {"name": "Alice", "score": null},
            ParseTest_NullableRecord,
            {name: "Alice", score: ()}
        ],

        // --- Category: unevaluatedProperties ---

        // 48. All extra properties are boolean
        [
            {"name": "Alice", "active": true, "deleted": false},
            ParseTest_UnevalPropsTest,
            {"name": "Alice", active: true, deleted: false}
        ],
        // 49. No extra properties (nothing to evaluate)
        [
            {"name": "Alice"},
            ParseTest_UnevalPropsTest,
            {"name": "Alice"}
        ],

        // --- Category: unevaluatedItems ---

        // 50. Contains 1 int in bounds, unevaluated items are boolean
        [
            <json>[50, true, false],
            ParseTest_UnevalItemsTest,
            [50, true, false]
        ],
        // 51. Contains 2 ints in bounds, no unevaluated items
        [
            <json>[50, 75],
            ParseTest_UnevalItemsTest,
            [50, 75]
        ],

        // --- Category: MissingArrayKeywordsSchema (unevaluatedItems) ---

        // 52. 2 ints >= 5 match contains, unevaluated are boolean
        [
            <json>[5, 6],
            MissingArrayKeywordsSchema,
            [5, 6]
        ],
        // 53. 2 ints >= 5 match contains, 2 booleans as unevaluated
        [
            <json>[5, 7, true, false],
            MissingArrayKeywordsSchema,
            [5, 7, true, false]
        ],

        // --- Category: exclusiveMinimum / exclusiveMaximum ---

        // 54. Value within exclusive range (1-99)
        [
            50,
            ParseTest_ExclusiveRange,
            50
        ],
        // 55. Value just above exclusiveMinimum
        [
            1,
            ParseTest_ExclusiveRange,
            1
        ],
        // 56. Value just below exclusiveMaximum
        [
            99,
            ParseTest_ExclusiveRange,
            99
        ],

        // --- Category: multipleOf ---

        // 57. Multiple of 5
        [
            25,
            ParseTest_MultipleOfFive,
            25
        ],
        // 58. Zero is multiple of everything
        [
            0,
            ParseTest_MultipleOfFive,
            0
        ],

        // --- Category: pattern ---

        // 59. Capitalized string matches pattern
        [
            "Hello",
            ParseTest_Capitalized,
            "Hello"
        ],
        // 60. Single capital letter followed by lowercase
        [
            "World",
            ParseTest_Capitalized,
            "World"
        ],

        // --- Category: maxItems ---

        // 61. Array within maxItems:3
        [
            <json>["a", "b", "c"],
            ParseTest_MaxThree,
            ["a", "b", "c"]
        ],
        // 62. Array with 1 item
        [
            <json>["a"],
            ParseTest_MaxThree,
            ["a"]
        ],

        // --- Category: unevaluatedItems with specific element type ---

        // 63. All items are strings, unevaluated items (none) pass boolean check
        [
            <json>["hello", "world"],
            ParseTest_UnevalItemsString,
            ["hello", "world"]
        ],
        // 64. Single string element
        [
            <json>["test"],
            ParseTest_UnevalItemsString,
            ["test"]
        ],

        // --- Category: unevaluatedItems with tuple ---

        // 65. Tuple elements match types, no unevaluated items
        [
            <json>["hello", 42],
            ParseTest_UnevalItemsTuple,
            ["hello", 42]
        ],
        // 66. Tuple + rest elements, rest is boolean
        [
            <json>["hello", 42, true, false],
            ParseTest_UnevalItemsTuple,
            ["hello", 42, true, false]
        ]
    ];
}

@test:Config {
    groups: ["parseAsType-schema-validation"],
    dataProvider: validParseAsTypeSchemaValidation
}
function testValidParseAsTypeSchemaValidation(json sourceData, typedesc<anydata> expType,
        anydata expResult) returns error? {
    anydata result = check parseAsType(sourceData, {allowDataProjection: false}, expType);
    test:assertEquals(result, expResult);
}

// ============================================================
// Invalid parseAsType cases (should return Error)
// ============================================================

function invalidParseAsTypeSchemaValidation() returns [json, typedesc<anydata>][] {
    return [
        // 1. name too short (minLength:2)
        [{"name": "A", "age": 25, "email": "x"}, ParseTest_ConstrainedRecord],
        // 2. name too long (maxLength:10)
        [{"name": "12345678901", "age": 25, "email": "x"}, ParseTest_ConstrainedRecord],
        // 3. age below minimum (0.0)
        [{"name": "Alice", "age": -1, "email": "x"}, ParseTest_ConstrainedRecord],
        // 4. age above maximum (100.0)
        [{"name": "Alice", "age": 101, "email": "x"}, ParseTest_ConstrainedRecord],

        // 5. String too short (minLength:1)
        ["", ParseTest_TagString],
        // 6. String too long (maxLength:5)
        ["toolong", ParseTest_TagString],
        // 7. Number below minimum
        [0, ParseTest_BoundedNumber],
        // 8. Number above maximum
        [101, ParseTest_BoundedNumber],

        // 9. minItems not met (Schema26 needs 10, has 9)
        [<json>[1, "a", "b", "c", "d", "e", "f", "g", "h"], Schema26],
        // 10. uniqueItems violated (Schema31)
        [<json>["apple", "banana", "apple"], Schema31],
        // 11. minItems not met (Schema61 needs 2, has 1)
        [<json>["admin"], Schema61],
        // 12. contains not satisfied (Schema62 needs string, has only ints)
        [<json>[1, 2, 3], Schema62],
        // 13. uniqueItems violated with contains (Schema32)
        [<json>["apple", "apple", "banana"], Schema32],

        // 14. String element too short (Schema21, minLength:5)
        [<json>["hi", "world", 25.0], Schema21],
        // 15. First element below minimum (Schema24, minimum:4.0)
        [<json>[3, "hello"], Schema24],

        // 16. PropertyNames pattern fails (uppercase)
        [{"FirstName": "John"}, Schema1],
        // 17. PropertyNames maxLength fails
        [{"veryLongPropertyName": 1}, Schema2],
        // 18. PropertyNames minLength fails
        [{"a": 1}, Schema3],
        // 19. minProperties not met (needs 2, has 1)
        [{"single": 1}, SchemaMinProps1],
        // 20. maxProperties exceeded (max 3, has 4)
        [{"a": 1, "b": 2, "c": 3, "d": 4}, SchemaMaxProps1],
        // 21. minMaxProperties below min
        [{"only": 1}, SchemaMinMaxProps1],
        // 22. minMaxProperties above max
        [{"a": 1, "b": 2, "c": 3, "d": 4}, SchemaMinMaxProps1],

        // 23. PatternProperties type mismatch (S_ expects string, got int)
        [{"S_field": 42}, SchemaPP1],
        // 24. AdditionalProperties(never) violated
        [{"extra": "not allowed"}, SchemaPP5],
        // 25. PatternProperties + AdditionalProperties both fail
        [{"allowed": "yes", "S_1": 10, "unmatched": "bad"}, SchemaPP5],
        // 26. Additional property type mismatch (expects boolean)
        [{"S_valid": "string", "invalid_additional": 123}, SchemaPP6],

        // 27. name too short (minLength:2)
        [{"name": "J", "age": 30}, ParseTest_PersonRecord],
        // 28. name too long (maxLength:5)
        [{"name": "Jonathan", "age": 30}, ParseTest_PersonRecord],
        // 29. age below minimum (0.0)
        [{"name": "John", "age": -5}, ParseTest_PersonRecord],
        // 30. age above maximum (150.0)
        [{"name": "John", "age": 200}, ParseTest_PersonRecord],

        // 31. Rest field empty string (minLength:1)
        [{"name": "test", "key1": ""}, ParseTest_ConstrainedRestRecord],

        // 32. Nested city too short
        [{"name": "Alice", "address": {"city": "C", "country": "LK"}},
            ParseTest_PersonWithAddress],
        // 33. Nested country too long
        [{"name": "Alice", "address": {"city": "Colombo", "country": "ABCDEFGHIJKLMNOPQRSTUVWXYZ"}},
            ParseTest_PersonWithAddress],

        // 34. Only matches RecordA (has name), missing RecordB (no age)
        [{"name": "Alice"}, ParseTest_AllOfTestType],
        // 35. Matches NotSchema (has "forbidden" field)
        [{"name": "Alice", "forbidden": "x"}, ParseTest_NotTestType],
        // 36. Missing required email (RequiredSchema1)
        [{"name": "John"}, RequiredSchema1],
        // 37. Missing required name (RequiredSchema1)
        [{"email": "john@example.com"}, RequiredSchema1],
        // 38. Missing all required (RequiredSchema2)
        [{}, RequiredSchema2],
        // 39. Missing required email (RequiredSchema2)
        [{"name": "Alice", "age": 35}, RequiredSchema2],

        // --- Category 12: Tuple constraints ---

        // 40. Second element negative (Schema59, minimum:0.0)
        [<json>["alice", -5], Schema59],

        // --- Category 13: DependentRequired ---

        // 41. name present but age missing (name requires age)
        [{"name": "John"}, DependentRequiredType],
        // 42. id present but name missing (id requires name)
        [{"id": 123}, DependentRequiredType],
        // 43. age present but id missing (age requires id)
        [{"age": 30}, DependentRequiredType],

        // --- Category 13: DependentSchema ---

        // 44. age present, name is int (AgeDependentSchema expects string)
        [{"age": 30, "name": 123}, DependentSchemaType],

        // --- Category 14: Readonly intersection with constraints ---

        // 45. name too short in readonly record
        [{"name": "A", "age": 30}, ParseTest_ReadonlyConstrained],

        // --- Category: Constrained array in record ---

        // 46. Negative score in array
        [{"name": "Alice", "scores": [90, -5, 95]}, ParseTest_ScoresRecord],

        // --- Category: Constrained tuple in record ---

        // 47. Empty tag in tuple
        [{"name": "Alice", "tags": ["java", ""]}, ParseTest_RecordWithTuple],

        // --- Category: Nullable field with constraint ---

        // 48. Score above maximum
        [{"name": "Alice", "score": 101}, ParseTest_NullableRecord],

        // --- Category: Array element constraints (Schema44) ---

        // --- Category: unevaluatedItems ---

        // 53. Unevaluated item is string, unevaluatedItems expects boolean
        [<json>[50, "hello"], ParseTest_UnevalItemsTest],
        // 54. Unevaluated item is int, unevaluatedItems expects boolean
        [<json>[50, 75, 99], ParseTest_UnevalItemsTest],
        // 55. No items match contains (minContains: 1)
        [<json>[true, false], ParseTest_UnevalItemsTest],

        // --- Category: MissingArrayKeywordsSchema (unevaluatedItems) ---

        // 56. Only 1 match contains (minContains: 2)
        [<json>[5], MissingArrayKeywordsSchema],
        // 57. 4 matches contains (maxContains: 3)
        [<json>[5, 6, 7, 8], MissingArrayKeywordsSchema],
        // 58. Unevaluated item is string, expects boolean
        [<json>[5, 6, "bad"], MissingArrayKeywordsSchema],
        // 59. Contains item below minimum (5.0)
        [<json>[4, 6], MissingArrayKeywordsSchema],

        // --- Category: exclusiveMinimum / exclusiveMaximum ---

        // 60. Value at exclusiveMinimum (0 is not > 0)
        [0, ParseTest_ExclusiveRange],
        // 61. Value at exclusiveMaximum (100 is not < 100)
        [100, ParseTest_ExclusiveRange],
        // 62. Value below exclusiveMinimum
        [-1, ParseTest_ExclusiveRange],
        // 63. Value above exclusiveMaximum
        [101, ParseTest_ExclusiveRange],

        // --- Category: multipleOf ---

        // 64. Not a multiple of 5
        [7, ParseTest_MultipleOfFive],
        // 65. Not a multiple of 5
        [3, ParseTest_MultipleOfFive],

        // --- Category: pattern ---

        // 66. All lowercase (no capital first letter)
        ["hello", ParseTest_Capitalized],
        // 67. All uppercase
        ["HELLO", ParseTest_Capitalized],
        // 68. Single lowercase letter
        ["h", ParseTest_Capitalized],

        // --- Category: maxItems ---

        // 69. Array exceeds maxItems:3
        [<json>["a", "b", "c", "d"], ParseTest_MaxThree],

        // --- Category: unevaluatedItems with specific element type ---

        // 70. String element where boolean expected as unevaluated (type mismatch fails type traversal)
        [<json>[123], ParseTest_UnevalItemsString],

        // --- Category: unevaluatedItems with tuple ---

        // 71. Rest element is string, unevaluatedItems expects boolean
        [<json>["hello", 42, "bad"], ParseTest_UnevalItemsTuple],
        // 72. Rest element is int, unevaluatedItems expects boolean
        [<json>["hello", 42, 99], ParseTest_UnevalItemsTuple]
    ];
}

@test:Config {
    groups: ["parseAsType-schema-validation"],
    dataProvider: invalidParseAsTypeSchemaValidation
}
function testInvalidParseAsTypeSchemaValidation(json sourceData, typedesc<anydata> expType) {
    anydata|Error result = parseAsType(sourceData, {allowDataProjection: false}, expType);
    test:assertTrue(result is Error,
        msg = "Expected parseAsType to fail with schema validation error but it succeeded");
}

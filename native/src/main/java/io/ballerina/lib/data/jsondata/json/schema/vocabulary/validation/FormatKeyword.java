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

package io.ballerina.lib.data.jsondata.json.schema.vocabulary.validation;

// TODO: NOT THE FINAL FORMAT CASES


import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;
import io.ballerina.runtime.api.values.BString;

import java.net.URISyntaxException;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FormatKeyword extends Keyword {
    public static final String keywordName = "format";
    private final String keywordValue;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );

    private static final Pattern IDN_EMAIL_PATTERN = Pattern.compile(
            "^[\\p{L}0-9_+&*-]+(?:\\.[\\p{L}0-9_+&*-]+)*@(?:[\\p{L}0-9-]+\\.)+[\\p{L}]{2,7}$"
    );

    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|" +
            "^::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|" +
            "^([0-9a-fA-F]{1,4}:){1,7}:$|" +
            "^([0-9a-fA-F]{1,4}:){0,6}::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$|" +
            "^([0-9a-fA-F]{1,4}:){0,6}::$"
    );

    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(?:\\.[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$"
    );

    private static final Pattern IDN_HOSTNAME_PATTERN = Pattern.compile(
            "^[\\p{L}0-9](?:[\\p{L}0-9-]{0,61}[\\p{L}0-9])?(?:\\.[\\p{L}0-9](?:[\\p{L}0-9-]{0,61}[\\p{L}0-9])?)*$"
    );

    private static final Pattern JSON_POINTER_PATTERN = Pattern.compile(
            "^(?:/(?:[^~/]|~0|~1)*)*$"
    );

    private static final Pattern RELATIVE_JSON_POINTER_PATTERN = Pattern.compile(
            "^(?:0|[1-9][0-9]*)(?:#|(?:/(?:[^~/]|~0|~1)*)*)$"
    );

    private static final Pattern URI_TEMPLATE_PATTERN = Pattern.compile(
            "^[^?#]*(?:\\?[^#]*)?(?:#.*)?$"
    );

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        if (!(instance instanceof BString str)) {
            return true;
        }
        boolean valid;
        switch (keywordValue) {
            case "email":
                valid = validateEmail(str.getValue());
                break;
            case "idn-email":
                valid = validateIdnEmail(str.getValue());
                break;
            case "ipv4":
                valid = validateIPv4(str.getValue());
                break;
            case "ipv6":
                valid = validateIPv6(str.getValue());
                break;
            case "uuid":
                valid = validateUUID(str.getValue());
                break;
            case "uri":
                valid = validateURI(str.getValue());
                break;
            case "uri-reference":
                valid = validateURIReference(str.getValue());
                break;
            case "iri":
                valid = validateIRI(str.getValue());
                break;
            case "iri-reference":
                valid = validateIRIReference(str.getValue());
                break;
            case "date-time":
                valid = validateDateTime(str.getValue());
                break;
            case "date":
                valid = validateDate(str.getValue());
                break;
            case "time":
                valid = validateTime(str.getValue());
                break;
            case "duration":
                valid = validateDuration(str.getValue());
                break;
            case "regex":
                valid = validateRegex(str.getValue());
                break;
            case "hostname":
                valid = validateHostname(str.getValue());
                break;
            case "idn-hostname":
                valid = validateIdnHostname(str.getValue());
                break;
            case "json-pointer":
                valid = validateJsonPointer(str.getValue());
                break;
            case "relative-json-pointer":
                valid = validateRelativeJsonPointer(str.getValue());
                break;
            case "uri-template":
                valid = validateUriTemplate(str.getValue());
                break;
            default:
                context.addError("format", "At " + context.getInstanceLocation() + ": [format] unknown format type: " + keywordValue);
                return false;
        }
        if (!valid) {
            context.addError("format", "At " + context.getInstanceLocation() + ": [format] value does not match format: " + keywordValue);
        }
        return valid;
    }

    private boolean validateEmail(String value) {
        Matcher matcher = EMAIL_PATTERN.matcher(value);
        return matcher.matches();
    }

    private boolean validateIdnEmail(String value) {
        Matcher matcher = IDN_EMAIL_PATTERN.matcher(value);
        return matcher.matches();
    }

    private boolean validateIPv4(String value) {
        Matcher matcher = IPV4_PATTERN.matcher(value);
        return matcher.matches();
    }

    private boolean validateIPv6(String value) {
        Matcher matcher = IPV6_PATTERN.matcher(value);
        return matcher.matches();
    }

    private boolean validateUUID(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean validateURI(String value) {
        try {
            URI uri = new URI(value);
            return uri.isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean validateURIReference(String value) {
        try {
            new URI(value);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean validateIRI(String value) {
        try {
            URI uri = new URI(value);
            return uri.isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean validateIRIReference(String value) {
        try {
            new URI(value);
            return true;
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private boolean validateDateTime(String value) {
        try {
            OffsetDateTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean validateDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean validateTime(String value) {
        try {
            OffsetTime.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean validateDuration(String value) {
        try {
            Duration.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private boolean validateRegex(String value) {
        try {
            Pattern.compile(value);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private boolean validateHostname(String value) {
        Matcher matcher = HOSTNAME_PATTERN.matcher(value);
        return matcher.matches();
    }

    private boolean validateIdnHostname(String value) {
        Matcher matcher = IDN_HOSTNAME_PATTERN.matcher(value);
        return matcher.matches();
    }

    private boolean validateJsonPointer(String value) {
        Matcher matcher = JSON_POINTER_PATTERN.matcher(value);
        return matcher.matches();
    }

    private boolean validateRelativeJsonPointer(String value) {
        Matcher matcher = RELATIVE_JSON_POINTER_PATTERN.matcher(value);
        return matcher.matches();
    }

    private boolean validateUriTemplate(String value) {
        Matcher matcher = URI_TEMPLATE_PATTERN.matcher(value);
        return matcher.matches();
    }

    public FormatKeyword(String keywordValue) {
        this.keywordValue = keywordValue;
    }

    @Override
    public String getKeywordValue() {
        return keywordValue;
    }
}

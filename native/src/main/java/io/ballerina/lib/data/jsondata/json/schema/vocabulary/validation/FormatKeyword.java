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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FormatKeyword extends Keyword {
    public static final String keywordName = "format";
    private final String keywordValue;

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "^P(?:(?:\\d+Y(?:\\d+M(?:\\d+D)?)?|\\d+M(?:\\d+D)?|\\d+D)" +
                    "(?:T(?=\\d)(?:\\d+H(?:\\d+M(?:\\d+S)?)?|\\d+M(?:\\d+S)?|\\d+S))?|T(?=\\d)(?:\\d+H(?:\\d+M(?:\\d+S)?)?|\\d+M(?:\\d+S)?|\\d+S)|\\d+W)$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern DOT_ATOM_LOCAL_PATTERN = Pattern.compile(
            "^[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern QUOTED_LOCAL_PATTERN = Pattern.compile(
            "^\"(?:[^\"\\\\]|\\\\.)*\"$"
    );

    private static final Pattern HOSTNAME_DOMAIN_PATTERN = Pattern.compile(
            "^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern IDN_EMAIL_PATTERN = Pattern.compile(
            "^[\\p{L}0-9_+&*-]+(?:\\.[\\p{L}0-9_+&*-]+)*@(?:[\\p{L}0-9-]+\\.)+[\\p{L}]{2,7}$"
    );


    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "(?i)^((([0-9a-f]{1,4}:){7}([0-9a-f]{1,4}|:))|(([0-9a-f]{1,4}:){6}(:[0-9a-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9a-f]{1,4}:){5}(((:[0-9a-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9a-f]{1,4}:){4}(((:[0-9a-f]{1,4}){1,3})|((:[0-9a-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9a-f]{1,4}:){3}(((:[0-9a-f]{1,4}){1,4})|((:[0-9a-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9a-f]{1,4}:){2}(((:[0-9a-f]{1,4}){1,5})|((:[0-9a-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9a-f]{1,4}:){1}(((:[0-9a-f]{1,4}){1,6})|((:[0-9a-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9a-f]{1,4}){1,7})|((:[0-9a-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))$"
    );

    private static final Pattern HOSTNAME_PATTERN = Pattern.compile(
            "(?i)^(?=.{1,253}$)[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[-0-9a-z]{0,61}[0-9a-z])?)*$"
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
            "^(?:(?:[^\\x00-\\x20\"'<>%\\\\^`{|}]|%[0-9a-f]{2})|\\{[+#./;?&=,!@|]?(?:[a-z0-9_]|%[0-9a-f]{2})+(?::[1-9][0-9]{0,3}|\\*)?(?:,(?:[a-z0-9_]|%[0-9a-f]{2})+(?::[1-9][0-9]{0,3}|\\*)?)*\\})*$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TIME_PATTERN = Pattern.compile(
            "^([0-2]\\d):([0-5]\\d):([0-5]\\d|60)(?:\\.\\d+)?([zZ]|[+-]\\d\\d:\\d\\d)$"
    );

    private static final Pattern DATETIME_PATTERN = Pattern.compile(
            "^(\\d{4})-([0-1]\\d)-([0-3]\\d)[tT]([0-2]\\d):([0-5]\\d):([0-5]\\d|60)" +
            "(?:\\.\\d+)?([zZ]|[+-]\\d\\d(?::?\\d\\d)?)$"
    );

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^(?:urn:uuid:)?[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern URI_PATTERN = Pattern.compile(
        "^[a-z][a-z0-9+\\-.]*:(?://(?:(?:[a-z0-9\\-._~!$&'()*+,;=:]|%[0-9a-f]{2})*@)?" +
            "(?:\\[(?:(?:(?:(?:[0-9a-f]{1,4}:){6}|::(?:[0-9a-f]{1,4}:){5}|" +
            "(?:[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){4}|" +
            "(?:(?:[0-9a-f]{1,4}:){0,1}[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){3}|" +
            "(?:(?:[0-9a-f]{1,4}:){0,2}[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){2}|" +
            "(?:(?:[0-9a-f]{1,4}:){0,3}[0-9a-f]{1,4})?::[0-9a-f]{1,4}:|" +
            "(?:(?:[0-9a-f]{1,4}:){0,4}[0-9a-f]{1,4})?::)" +
            "(?:[0-9a-f]{1,4}:[0-9a-f]{1,4}|(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}" +
            "(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?))|" +
            "(?:(?:[0-9a-f]{1,4}:){0,5}[0-9a-f]{1,4})?::[0-9a-f]{1,4}|" +
            "(?:(?:[0-9a-f]{1,4}:){0,6}[0-9a-f]{1,4})?::)|" +
            "[Vv][0-9a-f]+\\.[a-z0-9\\-._~!$&'()*+,;=:]+)\\]|" +
            "(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)|" +
            "(?:[a-z0-9\\-._~!$&'()*+,;=]|%[0-9a-f]{2})*)(?::\\d+)?" +
            "(?:/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*|" +
            "/(?:(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})+" +
            "(?:/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*)?|" +
            "(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})+" +
            "(?:/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*)" +
            "(?:\\?(?:[a-z0-9\\-._~!$&'()*+,;=:@/?]|%[0-9a-f]{2})*)?" +
            "(?:#(?:[a-z0-9\\-._~!$&'()*+,;=:@/?]|%[0-9a-f]{2})*)?$",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern URI_REFERENCE_PATTERN = Pattern.compile(
            "^(?:[a-z][a-z0-9+\\-.]*:)?(?:/?/(?:(?:[a-z0-9\\-._~!$&'()*+,;=:]|%[0-9a-f]{2})*@)?" +
            "(?:\\[(?:(?:(?:(?:[0-9a-f]{1,4}:){6}|::(?:[0-9a-f]{1,4}:){5}|" +
            "(?:[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){4}|" +
            "(?:(?:[0-9a-f]{1,4}:){0,1}[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){3}|" +
            "(?:(?:[0-9a-f]{1,4}:){0,2}[0-9a-f]{1,4})?::(?:[0-9a-f]{1,4}:){2}|" +
            "(?:(?:[0-9a-f]{1,4}:){0,3}[0-9a-f]{1,4})?::[0-9a-f]{1,4}:|" +
            "(?:(?:[0-9a-f]{1,4}:){0,4}[0-9a-f]{1,4})?::)" +
            "(?:[0-9a-f]{1,4}:[0-9a-f]{1,4}|(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}" +
            "(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?))|" +
            "(?:(?:[0-9a-f]{1,4}:){0,5}[0-9a-f]{1,4})?::[0-9a-f]{1,4}|" +
            "(?:(?:[0-9a-f]{1,4}:){0,6}[0-9a-f]{1,4})?::)|" +
            "[Vv][0-9a-f]+\\.[a-z0-9\\-._~!$&'()*+,;=:]+)\\]|" +
            "(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)|" +
            "(?:[a-z0-9\\-._~!$&'()*+,;=]|%[0-9a-f]{2})*)(?::\\d+)?" +
            "(?:/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*|" +
            "/(?:(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})+" +
            "(?:/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*)?|" +
            "(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})+" +
            "(?:/(?:[a-z0-9\\-._~!$&'()*+,;=:@]|%[0-9a-f]{2})*)*)?" +
            "(?:\\?(?:[a-z0-9\\-._~!$&'()*+,;=:@/?]|%[0-9a-f]{2})*)?" +
            "(?:#(?:[a-z0-9\\-._~!$&'()*+,;=:@/?]|%[0-9a-f]{2})*)?$",
            Pattern.CASE_INSENSITIVE
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
        int atIndex = findEmailSeparator(value);
        if (atIndex <= 0 || atIndex >= value.length() - 1) {
            return false;
        }
        String local = value.substring(0, atIndex);
        String domain = value.substring(atIndex + 1);
        return isValidEmailLocalPart(local) && isValidEmailDomain(domain);
    }

    private int findEmailSeparator(String value) {
        if (value.startsWith("\"")) {
            int i = 1;
            while (i < value.length()) {
                char c = value.charAt(i);
                if (c == '\\' && i + 1 < value.length()) {
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    if (i + 1 < value.length() && value.charAt(i + 1) == '@') {
                        return i + 1;
                    }
                    return -1;
                }
                i++;
            }
            return -1;
        }
        return value.lastIndexOf('@');
    }

    private boolean isValidEmailLocalPart(String local) {
        if (local.startsWith("\"")) {
            return QUOTED_LOCAL_PATTERN.matcher(local).matches();
        }
        return DOT_ATOM_LOCAL_PATTERN.matcher(local).matches();
    }

    private boolean isValidEmailDomain(String domain) {
        if (domain.startsWith("[") && domain.endsWith("]")) {
            String inner = domain.substring(1, domain.length() - 1);
            if (inner.startsWith("IPv6:")) {
                return IPV6_PATTERN.matcher(inner.substring(5)).matches();
            }
            return IPV4_PATTERN.matcher(inner).matches();
        }
        return HOSTNAME_DOMAIN_PATTERN.matcher(domain).matches();
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
        return UUID_PATTERN.matcher(value).matches();
    }

    private boolean validateURI(String value) {
        if (value.indexOf('/') == -1 && value.indexOf(':') == -1) {
            return false;
        }
        return URI_PATTERN.matcher(value).matches();
    }

    private boolean validateURIReference(String value) {
        return URI_REFERENCE_PATTERN.matcher(value).matches();
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
        Matcher m = DATETIME_PATTERN.matcher(value);
        if (!m.matches()) {
            return false;
        }
        int year = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        int day = Integer.parseInt(m.group(3));
        int hour = Integer.parseInt(m.group(4));
        int second = Integer.parseInt(m.group(6));
        String offset = m.group(7);

        if (hour > 23) {
            return false;
        }
        try {
            LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            return false;
        }
        if (!offset.equalsIgnoreCase("Z")) {
            int offHour = Integer.parseInt(offset.substring(1, 3));
            if (offHour > 23) {
                return false;
            }
            if (offset.length() > 3) {
                int minuteStart = offset.length() == 6 ? 4 : 3;
                int offMinute = Integer.parseInt(offset.substring(minuteStart, minuteStart + 2));
                if (offMinute > 59) {
                    return false;
                }
            }
        }
        if (second == 60) {
            int minute = Integer.parseInt(m.group(5));
            return isLeapSecondValidInUtc(hour, minute, offset);
        }
        return true;
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
        Matcher m = TIME_PATTERN.matcher(value);
        if (!m.matches()) {
            return false;
        }
        int hour = Integer.parseInt(m.group(1));
        int second = Integer.parseInt(m.group(3));
        String offset = m.group(4);
        if (hour > 23) {
            return false;
        }
        if (!offset.equalsIgnoreCase("Z")) {
            int offHour = Integer.parseInt(offset.substring(1, 3));
            int offMinute = Integer.parseInt(offset.substring(4, 6));
            if (offHour > 23 || offMinute > 59) {
                return false;
            }
        }
        if (second == 60) {
            int minute = Integer.parseInt(m.group(2));
            return isLeapSecondValidInUtc(hour, minute, offset);
        }
        return true;
    }

    private boolean isLeapSecondValidInUtc(int hour, int minute, String offset) {
        int utcMinutes = hour * 60 + minute;
        if (!offset.equalsIgnoreCase("Z")) {
            int offSign = offset.charAt(0) == '+' ? -1 : 1;
            int offHour = Integer.parseInt(offset.substring(1, 3));
            int offMinute = 0;
            if (offset.length() > 3) {
                int minuteStart = offset.length() == 6 ? 4 : 3;
                offMinute = Integer.parseInt(offset.substring(minuteStart, minuteStart + 2));
            }
            utcMinutes += offSign * (offHour * 60 + offMinute);
        }
        utcMinutes = ((utcMinutes % 1440) + 1440) % 1440;
        return utcMinutes == 23 * 60 + 59;
    }

    private boolean validateDuration(String value) {
        Matcher matcher = DURATION_PATTERN.matcher(value);
        return matcher.matches();
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

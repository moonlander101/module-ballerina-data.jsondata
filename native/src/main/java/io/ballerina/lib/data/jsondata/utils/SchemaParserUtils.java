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

package io.ballerina.lib.data.jsondata.utils;

import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BDecimal;
import io.ballerina.runtime.api.values.BMap;
import io.ballerina.runtime.api.values.BString;

import java.math.BigDecimal;
import java.util.Optional;

public class SchemaParserUtils { // consider protected

    public static final String VALID_ANCHOR_REGEX = "^[A-Za-z_][A-Za-z0-9_.-]*$";

    public static Long toInteger(Object value) {
        if (value instanceof Long l) {
            return l;
        } else if (value instanceof Double d) {
            return (d % 1) == 0 ? d.longValue() : null;
        } else if (value instanceof BDecimal bd) {
            BigDecimal javaDecimal = bd.decimalValue();
            boolean isInt = javaDecimal.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) == 0;
            return isInt ? javaDecimal.longValue() : null;
        }
        return null;
    }

    public static Double toNumber(Object value) {
        if (value instanceof Long l) {
            return l.doubleValue();
        }
        if (value instanceof Double d) {
            return d;
        }
        if (value instanceof BDecimal bd) {
            return bd.decimalValue().doubleValue();
        }
        return null;
    }

    public static boolean isValidAnchorName(String anchor) {
        if (anchor == null || anchor.isEmpty()) {
            return false;
        }
        return anchor.matches(VALID_ANCHOR_REGEX);
    }

    public static Long extractInteger(BMap<BString, Object> json, String keyName) {
        BString bKey = StringUtils.fromString(keyName);
        if (!json.containsKey(bKey)) {
            return null;
        }
        return toInteger(json.get(bKey));
    }

    public static Optional<Long> extractLong(BMap<BString, Object> annotation, String keyName) {
        BString key = StringUtils.fromString(keyName);

        if (!annotation.containsKey(key)) {
            return Optional.empty();
        }

        Object value = annotation.get(key);

        if (value instanceof Long longVal) {
            return Optional.of(longVal);
        }

        return Optional.empty();
    }

    public static Optional<Double> extractDouble(BMap<BString, Object> annotation, String keyName) {
        BString key = StringUtils.fromString(keyName);

        if (!annotation.containsKey(key)) {
            return Optional.empty();
        }

        Object value = annotation.get(key);

        if (value instanceof Long longVal) {
            return Optional.of(longVal.doubleValue());
        } else if (value instanceof Double doubleVal) {
            return Optional.of(doubleVal);
        } else if (value instanceof BDecimal decimalVal) {
            return Optional.of(decimalVal.decimalValue().doubleValue());
        }

        return Optional.empty();
    }

    public static Optional<Boolean> extractBoolean(BMap<BString, Object> annotation, String keyName) {
        BString key = StringUtils.fromString(keyName);

        if (!annotation.containsKey(key)) {
            return Optional.empty();
        }

        Object value = annotation.get(key);

        if (value instanceof Boolean boolVal) {
            return Optional.of(boolVal);
        }

        return Optional.empty();
    }
}

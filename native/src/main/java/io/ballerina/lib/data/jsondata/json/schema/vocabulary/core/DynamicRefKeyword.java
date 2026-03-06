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

package io.ballerina.lib.data.jsondata.json.schema.vocabulary.core;

import io.ballerina.lib.data.jsondata.json.schema.EvaluationContext;
import io.ballerina.lib.data.jsondata.json.schema.Schema;
import io.ballerina.lib.data.jsondata.json.schema.SchemaRegistry;
import io.ballerina.lib.data.jsondata.json.schema.Validator;
import io.ballerina.lib.data.jsondata.json.schema.vocabulary.Keyword;

import java.net.URI;
import java.util.ArrayList;


public class DynamicRefKeyword extends Keyword {
    public static final String keywordName = "$dynamicRef";
    private final URI initialRefUri;
    private final String anchorName;

    public DynamicRefKeyword(URI initialRefUri, String anchorName) {
        this.initialRefUri = initialRefUri;
        this.anchorName = anchorName;
    }

    @Override
    public Object getKeywordValue() {
        return initialRefUri.toString();
    }

    @Override
    public boolean evaluate(Object instance, EvaluationContext context) {
        SchemaRegistry registry = context.getSchemaRegistry();
        if (registry == null) {
            context.addError(keywordName,
                    "At " + context.getInstanceLocation()
                            + ": schema registry is required for $dynamicRef resolution");
            return false;
        }

        Object target = null;
        if (anchorName != null && registry.isDynamicAnchor(initialRefUri)) {
            ArrayList<URI> scopeArray = context.getDynamicScope();
            String lastSeenResource = null;
            for (int i = scopeArray.size() - 1; i >= 0; i--) {
                String resourceStr = stripFragment(scopeArray.get(i));
                if (resourceStr.equals(lastSeenResource)) {
                    continue; // skip duplicate resource entries in scope
                }
                lastSeenResource = resourceStr;
                URI candidateUri;
                try {
                    candidateUri = URI.create(resourceStr + "#" + anchorName);
                } catch (IllegalArgumentException e) {
                    continue;
                }
                if (registry.isDynamicAnchor(candidateUri)) {
                    Object candidate = registry.get(candidateUri);
                    if (candidate != null) {
                        target = candidate;
                        break;
                    }
                }
            }
        }

        if (target == null) {
            target = registry.resolve(initialRefUri);
        }

        if (target == null) {
            context.addError(keywordName,
                    "At " + context.getInstanceLocation()
                            + ": unresolved $dynamicRef '" + initialRefUri + "'");
            return false;
        }

        URI resourceUri = getResourceUri(target);
        boolean pushed = false;
        if (resourceUri != null) {
            context.pushDynamicScope(resourceUri);
            pushed = true;
        }
        try {
            EvaluationContext refContext = context.createChildContext("", keywordName);
            return new Validator(false).validate(instance, target, refContext);
        } finally {
            if (pushed) {
                context.popDynamicScope();
            }
        }
    }

    private static String stripFragment(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getSchemeSpecificPart(), null).toString();
        } catch (Exception e) {
            String s = uri.toString();
            int hash = s.indexOf('#');
            return hash >= 0 ? s.substring(0, hash) : s;
        }
    }

    private static URI getResourceUri(Object target) {
        if (!(target instanceof Schema schema)) {
            return null;
        }
        Keyword idKeyword = schema.getKeyword(IdKeyword.keywordName);
        if (idKeyword == null) {
            return null;
        }
        Object idValue = idKeyword.getKeywordValue();
        if (idValue == null) {
            return null;
        }
        String idStr = idValue.toString();
        // Strip any fragment — the resource URI is the base without fragment
        try {
            URI full = URI.create(idStr);
            return new URI(full.getScheme(), full.getSchemeSpecificPart(), null);
        } catch (Exception e) {
            return null;
        }
    }
}

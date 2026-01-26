/*
 * Copyright (c) 2026, WSO2 LLC. (https://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerina.lib.data.jsondata.json.schema;

import com.networknt.schema.AbsoluteIri;
import com.networknt.schema.resource.SchemaIdResolver;
import io.ballerina.lib.data.jsondata.utils.DiagnosticErrorCode;
import io.ballerina.lib.data.jsondata.utils.DiagnosticLog;
import io.ballerina.lib.data.jsondata.utils.SchemaValidatorUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class RetrievalUriResolver implements SchemaIdResolver {
    private final Map<String, String> idToPath = new HashMap<>();

    public RetrievalUriResolver(String firstFilePath) {
        Path filePath = new File(firstFilePath).toPath();
        if (Files.isDirectory(filePath))  {
            throw DiagnosticLog.error(DiagnosticErrorCode.INVALID_SCHEMA_FILE_TYPE, firstFilePath);
        }
        Path rootDir = filePath.getParent();
        initialDiscovery(rootDir);
    }

    @Override
    public AbsoluteIri resolve(AbsoluteIri targetId) {
        String idStr = targetId.toString();
        if (idToPath.containsKey(idStr)) {
            String resolvedPath = idToPath.get(idStr);
            return AbsoluteIri.of(resolvedPath);
        }
        return AbsoluteIri.of(idToPath.getOrDefault(idStr, idStr));
    }

    public void initialDiscovery(Path rootDir) {
        int MAX_DEPTH = 2;
        try (Stream<Path> paths = Files.walk(rootDir, MAX_DEPTH + 1)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        String topLevelId = SchemaValidatorUtils.extractRootIdFromJson(path);
                        if (!topLevelId.isEmpty()) {
                            String normalizedUri = path.toUri().normalize().toString();
                            this.idToPath.put(topLevelId, normalizedUri);
                        }
                    });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class RetrievalUriResolver implements SchemaIdResolver {
    private final Map<String, String> idToPath = new HashMap<>();

    public RetrievalUriResolver(String firstFilePath) {
        Path filePath = new File(firstFilePath).toPath().toAbsolutePath().normalize();
        if (Files.isDirectory(filePath))  {
            throw new RuntimeException("The provided path is a directory, expected a file path: " + firstFilePath);
        }
        Path rootDir = filePath.getParent();
        System.out.println("Root directory for schema discovery: " + rootDir);
        initialDiscovery(rootDir);
    }

    @Override
    public AbsoluteIri resolve(AbsoluteIri targetId) {
        String idStr = targetId.toString();
        if (idToPath.containsKey(idStr)) {
            return AbsoluteIri.of(idToPath.get(idStr));
        }
        return AbsoluteIri.of(idToPath.getOrDefault(idStr, idStr));
    }

    public void initialDiscovery(Path rootDir) {
        try (Stream<Path> paths = Files.walk(rootDir)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        String id = extractIdFromJson(path);
                        if (id != null) {
                            this.idToPath.put(id, path.toUri().toString());
                        }
                    });

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        System.out.println("HashMap after initial discovery: " + idToPath);
    }

    public String extractIdFromJson(Path jsonFilePath) {
        try {
            String content = Files.readString(jsonFilePath);
            int idIndex = content.indexOf("\"$id\"");
            if (idIndex != -1) {
                int colonIndex = content.indexOf(":", idIndex);
                int startQuoteIndex = content.indexOf("\"", colonIndex);
                int endQuoteIndex = content.indexOf("\"", startQuoteIndex + 1);
                if (startQuoteIndex != -1 && endQuoteIndex != -1) {
                    return content.substring(startQuoteIndex + 1, endQuoteIndex);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read JSON file: " + jsonFilePath, e);
        }
        return null;
    }
}

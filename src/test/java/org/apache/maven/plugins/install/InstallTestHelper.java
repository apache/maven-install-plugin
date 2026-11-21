/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.install;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InstallTestHelper {
    static void assertNoChecksumFiles(Path repositoryRoot) throws Exception {
        if (!Files.exists(repositoryRoot)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(repositoryRoot)) {
            assertTrue(
                    paths.noneMatch(p -> {
                        String name = p.getFileName().toString();
                        return name.endsWith(".md5") || name.endsWith(".sha1");
                    }),
                    "no .md5/.sha1 checksum files should be created");
        }
    }
}

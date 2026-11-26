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

import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

// install:install of a jar project with an attached sources jar writes the real files to the local
// repository. The unit tests mock the installer, so this is the only place that checks the result.
File groupDir = new File(localRepositoryPath, "org/apache/maven/its/install/ajs")
File artifactDir = new File(groupDir, "test")
File versionDir = new File(artifactDir, "1.0-SNAPSHOT")

for (String path : [
        "test-1.0-SNAPSHOT.jar",
        "test-1.0-SNAPSHOT-sources.jar",
        "test-1.0-SNAPSHOT.pom",
        "maven-metadata-local.xml"]) {
    File file = new File(versionDir, path)
    assert file.isFile() : "Missing: " + file.getAbsolutePath()
}
File artifactMetadata = new File(artifactDir, "maven-metadata-local.xml")
assert artifactMetadata.isFile() : "Missing: " + artifactMetadata.getAbsolutePath()

// the files are the build outputs, not placeholders
String pom = new File(versionDir, "test-1.0-SNAPSHOT.pom").getText(StandardCharsets.UTF_8.name())
assert pom.contains("<artifactId>test</artifactId>") : "Unexpected installed POM:\n" + pom
String metadata = artifactMetadata.getText(StandardCharsets.UTF_8.name())
assert metadata.contains("<version>1.0-SNAPSHOT</version>") : "Unexpected artifact metadata:\n" + metadata
new ZipFile(new File(versionDir, "test-1.0-SNAPSHOT-sources.jar")).withCloseable { zip ->
    assert zip.getEntry("org/apache/maven/its/install/ajs/Hello.java") != null : "Sources jar lacks Hello.java"
}
new ZipFile(new File(versionDir, "test-1.0-SNAPSHOT.jar")).withCloseable { zip ->
    assert zip.getEntry("org/apache/maven/its/install/ajs/Hello.class") != null : "Jar lacks Hello.class"
}

// the installer must not write checksum files next to any of them
List<String> checksums = []
groupDir.eachFileRecurse { File file ->
    if (file.name.endsWith(".md5") || file.name.endsWith(".sha1")) {
        checksums << file.getAbsolutePath()
    }
}
assert checksums.isEmpty() : "Unexpected checksum files: " + checksums

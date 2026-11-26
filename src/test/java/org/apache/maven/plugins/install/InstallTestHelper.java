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

import java.io.File;
import java.nio.file.Path;

class InstallTestHelper {
    /**
     * Location of the version-level {@code maven-metadata-local.xml}, relative to the local repository root.
     *
     * <p>The Maven 4 {@code @MojoTest} harness mocks the real {@code ArtifactInstaller}, and that installer is
     * the component which actually writes {@code maven-metadata-local.xml}. No metadata file is therefore
     * created in the mock local repository during these tests. The metadata location is derived solely from
     * the installed artifact's group/artifact/version, so the ITs' "metadata exists at path X" assertions are
     * expressed here in terms of the coordinate-derived path the install request hands to the session.
     *
     * @param localArtifactPath path returned by {@code Session#getPathForLocalArtifact} for the installed POM
     * @return the directory of the artifact version plus the metadata file name
     */
    static Path versionLevelMetadataPath(Path localArtifactPath) {
        return localArtifactPath.getParent().resolve("maven-metadata-local.xml");
    }

    /**
     * Location of the group-level {@code maven-metadata-local.xml}. See
     * {@link #versionLevelMetadataPath(Path)} for why this is derived from coordinates rather than the file system.
     *
     * @param localArtifactPath path returned by {@code Session#getPathForLocalArtifact} for the installed POM
     * @return the group/artifact directory plus the metadata file name
     */
    static Path groupLevelMetadataPath(Path localArtifactPath) {
        return localArtifactPath.getParent().getParent().resolve("maven-metadata-local.xml");
    }

    /**
     * Turns a (possibly Jimfs-provided) local-repository path into a stable, slash-separated relative path so it
     * can be compared with the literal paths asserted by the converted integration tests.
     */
    static String repoRelativePath(Path path, String localRepoPrefix) {
        String value = path.toString().replace(File.separator, "/");
        String prefix = localRepoPrefix.replace(File.separator, "/");
        while (prefix.endsWith("/")) {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        prefix += "/";
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }
}

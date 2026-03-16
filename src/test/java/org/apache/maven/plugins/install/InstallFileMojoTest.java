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

import javax.inject.Inject;

import java.io.File;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest(realRepositorySession = true)
@Basedir("/unit/install-file-test")
@MojoParameter(name = "groupId", value = "org.apache.maven.test")
@MojoParameter(name = "artifactId", value = "maven-install-file-test")
@MojoParameter(name = "version", value = "1.0-SNAPSHOT")
public class InstallFileMojoTest {

    @TempDir
    private Path tempDir;

    private File localRepo;
    private File specificLocalRepositoryPath;

    @Inject
    private MavenSession mavenSession;

    @BeforeEach
    public void setUp() throws Exception {
        localRepo = tempDir.resolve("local-repo").toAbsolutePath().toFile();
        mavenSession.getRequest().setLocalRepositoryPath(localRepo);

        specificLocalRepositoryPath =
                tempDir.resolve("specific-local-repo").toAbsolutePath().toFile();
        mavenSession
                .getUserProperties()
                .setProperty("specificLocalRepositoryPath", specificLocalRepositoryPath.getAbsolutePath());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    @MojoParameter(name = "localRepositoryPath", value = "${specificLocalRepositoryPath}")
    public void testInstallFileWithLocalRepositoryPath(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        File installedArtifact = new File(
                specificLocalRepositoryPath,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.jar");

        assertTrue(
                installedArtifact.exists(), "Installed artifact should exist: " + installedArtifact.getAbsolutePath());

        assertEquals(
                5, FileUtils.getFiles(specificLocalRepositoryPath, null, null).size());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void testBasicInstallFile(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        File installedArtifact = new File(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.jar");

        assertTrue(
                installedArtifact.exists(), "Installed artifact should exist: " + installedArtifact.getAbsolutePath());

        assertEquals(5, FileUtils.getFiles(localRepo, null, null).size());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "classifier", value = "sources")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void testInstallFileWithClassifier(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        File installedArtifact = new File(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT-sources.jar");

        assertTrue(
                installedArtifact.exists(), "Installed artifact should exist: " + installedArtifact.getAbsolutePath());

        assertEquals(5, FileUtils.getFiles(localRepo, null, null).size());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "generatePom", value = "true")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void testInstallFileWithGeneratePom(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        File installedArtifact = new File(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.jar");

        assertTrue(
                installedArtifact.exists(), "Installed artifact should exist: " + installedArtifact.getAbsolutePath());

        File installedPom = new File(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.pom");

        try (Reader reader = new XmlStreamReader(installedPom)) {
            Model model = new MavenXpp3Reader().read(reader);

            assertEquals("4.0.0", model.getModelVersion());
            assertEquals("org.apache.maven.test", model.getGroupId());
            assertEquals("maven-install-file-test", model.getArtifactId());
            assertEquals("1.0-SNAPSHOT", model.getVersion());
        }

        assertEquals(5, FileUtils.getFiles(localRepo, null, null).size());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    @MojoParameter(name = "pomFile", value = "${basedir}/plugin-config.xml")
    void testInstallFileWithPomFile(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        File installedArtifact = new File(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.jar");

        assertTrue(
                installedArtifact.exists(), "Installed artifact should exist: " + installedArtifact.getAbsolutePath());

        File installedPom = new File(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.pom");

        assertTrue(installedPom.exists(), "Installed pom should exist: " + installedPom.getAbsolutePath());

        // Compare the contents of the installed pom with the original pom file
        assertEquals(
                Files.readAllLines(
                        MojoExtension.getTestFile("plugin-config.xml").toPath()),
                Files.readAllLines(installedPom.toPath()));

        assertEquals(5, FileUtils.getFiles(localRepo, null, null).size());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "pom")
    @MojoParameter(name = "file", value = "${basedir}/plugin-config.xml")
    void testInstallFileWithPomAsPackaging(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        File installedPom = new File(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.pom");

        assertTrue(installedPom.exists(), "Installed pom should exist: " + installedPom.getAbsolutePath());

        // Compare the contents of the installed pom with the original pom file
        assertEquals(
                Files.readAllLines(
                        MojoExtension.getTestFile("plugin-config.xml").toPath()),
                Files.readAllLines(installedPom.toPath()));

        assertEquals(4, FileUtils.getFiles(localRepo, null, null).size());
    }
}

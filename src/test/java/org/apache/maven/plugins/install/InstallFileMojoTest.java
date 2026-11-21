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
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoExtension;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // -------------------------------------------------------------------------
    // Security hardening tests (f001, f002, f003, f004)
    // -------------------------------------------------------------------------

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void adoptsCoordinatesFromConsistentEmbeddedPom(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        // Clear class-level coordinates so the embedded POM is the sole source
        setMojoField(mojo, "groupId", null);
        setMojoField(mojo, "artifactId", null);
        setMojoField(mojo, "version", null);
        Path jar = createJarWithEntries(
                pomXml("org.example", "embedded-lib", "1.0"), "META-INF/maven/org.example/embedded-lib/pom.xml");
        setMojoField(mojo, "file", jar.toFile());

        mojo.execute();

        assertEquals("org.example", getMojoField(mojo, "groupId"));
        assertEquals("embedded-lib", getMojoField(mojo, "artifactId"));
        assertEquals("1.0", getMojoField(mojo, "version"));
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsEmbeddedPomWithMismatchedEntryPath(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        setMojoField(mojo, "groupId", null);
        setMojoField(mojo, "artifactId", null);
        setMojoField(mojo, "version", null);
        // Decoy entry path does not match the coordinates the embedded POM declares
        Path jar = createJarWithEntries(
                pomXml("org.apache.maven.plugins", "maven-clean-plugin", "3.4.0"),
                "META-INF/maven/org.evil/decoy/pom.xml");
        setMojoField(mojo, "file", jar.toFile());

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("entry path"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsJarWithMultipleEmbeddedPoms(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        setMojoField(mojo, "groupId", null);
        setMojoField(mojo, "artifactId", null);
        setMojoField(mojo, "version", null);
        Path jar = createJarWithEntries(
                pomXml("org.example", "embedded-lib", "1.0"),
                "META-INF/maven/org.example/embedded-lib/pom.xml",
                "META-INF/maven/org.other/other-lib/pom.xml");
        setMojoField(mojo, "file", jar.toFile());

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("POM entries"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar!")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void invalidPackagingRejected(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void embeddedPomIgnoredWhenFullCoordinatesSupplied(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        // Hostile embedded POM carrying a foreign GAV and an injected dependency
        String evilPom = "<project>" + "<modelVersion>4.0.0</modelVersion>"
                + "<groupId>com.evil</groupId>"
                + "<artifactId>injected</artifactId>"
                + "<version>9.9</version>"
                + "<packaging>jar</packaging>"
                + "<dependencies><dependency>"
                + "<groupId>com.evil</groupId><artifactId>backdoor</artifactId><version>1.0</version>"
                + "</dependency></dependencies>"
                + "</project>";
        Path jar = createJarWithEntries(evilPom, "META-INF/maven/com.evil/injected/pom.xml");
        setMojoField(mojo, "file", jar.toFile());

        // Full coordinates supplied via annotations: the embedded POM must be ignored
        mojo.execute();

        // Verify the installed POM is the generated one, not the evil embedded one
        File installedPom = new File(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.pom");
        assertTrue(installedPom.exists(), "Generated POM should be installed");
        try (Reader reader = new XmlStreamReader(installedPom)) {
            Model model = new MavenXpp3Reader().read(reader);
            assertEquals("org.apache.maven.test", model.getGroupId());
            assertEquals("maven-install-file-test", model.getArtifactId());
            assertEquals("1.0-SNAPSHOT", model.getVersion());
            assertTrue(model.getDependencies().isEmpty(), "Generated POM should have no dependencies");
        }
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void mismatchedEmbeddedPomRejectedWhenPackagingOmitted(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        // Packaging omitted (not set via annotation), so the embedded POM is consulted.
        // The attacker POM is internally consistent (entry path matches its own declared GAV)
        // but disagrees with the supplied coordinates (g/a/v from class level).
        Path jar =
                createJarWithEntries(pomXml("com.evil", "injected", "9.9"), "META-INF/maven/com.evil/injected/pom.xml");
        setMojoField(mojo, "file", jar.toFile());

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("does not match the supplied coordinates"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void matchingEmbeddedPomAcceptedWhenPackagingOmitted(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        // Packaging omitted; the embedded POM agrees with every supplied coordinate, so it may
        // be used and contribute the missing packaging
        Path jar = createJarWithEntries(
                pomXml("org.apache.maven.test", "maven-install-file-test", "1.0-SNAPSHOT"),
                "META-INF/maven/org.apache.maven.test/maven-install-file-test/pom.xml");
        setMojoField(mojo, "file", jar.toFile());

        mojo.execute();

        assertEquals("jar", getMojoField(mojo, "packaging"));
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsEmbeddedPomWithDoctype(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        setMojoField(mojo, "groupId", null);
        setMojoField(mojo, "artifactId", null);
        setMojoField(mojo, "version", null);
        String xxePom = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE project [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + pomXml("org.example", "embedded-lib", "1.0");
        Path jar = createJarWithEntries(xxePom, "META-INF/maven/org.example/embedded-lib/pom.xml");
        setMojoField(mojo, "file", jar.toFile());

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("DOCTYPE"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsEmbeddedPomWithDoctypeUtf16Le(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        setMojoField(mojo, "groupId", null);
        setMojoField(mojo, "artifactId", null);
        setMojoField(mojo, "version", null);
        String xxePom = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE project [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + pomXml("org.example", "embedded-lib", "1.0");
        // BOM-prefixed UTF-16LE: interleaved NUL bytes defeat a single-byte substring scan
        byte[] bytes = ("﻿" + xxePom).getBytes(StandardCharsets.UTF_16LE);
        Path jar = createJarWithEntryBytes(bytes, "META-INF/maven/org.example/embedded-lib/pom.xml");
        setMojoField(mojo, "file", jar.toFile());

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("DOCTYPE"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsEmbeddedPomWithDoctypeUtf16Be(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        setMojoField(mojo, "groupId", null);
        setMojoField(mojo, "artifactId", null);
        setMojoField(mojo, "version", null);
        String xxePom = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE project [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + pomXml("org.example", "embedded-lib", "1.0");
        // BOM-prefixed UTF-16BE
        byte[] bytes = ("﻿" + xxePom).getBytes(StandardCharsets.UTF_16BE);
        Path jar = createJarWithEntryBytes(bytes, "META-INF/maven/org.example/embedded-lib/pom.xml");
        setMojoField(mojo, "file", jar.toFile());

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("DOCTYPE"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = ".tmp.evil")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void groupIdWithLeadingDotRejected(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org..evil")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void groupIdWithConsecutiveDotsRejected(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "artifactId", value = "..")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void dotDotArtifactIdRejected(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "version", value = "..")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void dotDotVersionRejected(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

    // -------------------------------------------------------------------------
    // Tests converted from src/it (see the IT-to-unit mapping in the report)
    // -------------------------------------------------------------------------

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void invalidGavRejectedAndNothingInstalled(InstallFileMojo mojo) throws Exception {
        setMojoField(mojo, "groupId", "invalid~group");
        setMojoField(mojo, "artifactId", "invalid/artifactId");
        setMojoField(mojo, "version", "1+");

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("The artifact information is not valid"), e.getMessage());
        assertFalse(localRepo.exists(), "Nothing should be installed into " + localRepo);
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void missingCoordinatesRejectedAndNothingInstalled(InstallFileMojo mojo) throws Exception {
        setMojoField(mojo, "groupId", "groupId");
        setMojoField(mojo, "artifactId", null);
        setMojoField(mojo, "version", null);
        setMojoField(mojo, "packaging", null);
        // A jar without an embedded POM, like the test.jar of the gav-validation IT.
        setMojoField(mojo, "file", createJarWithEntries("test", "test.txt").toFile());

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertTrue(e.getMessage().contains("The artifact information is incomplete"), e.getMessage());
        assertFalse(localRepo.exists(), "Nothing should be installed into " + localRepo);
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void generatePomAutoInstallsJarAndPom(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        assertInstalled(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/"
                        + "maven-install-file-test-1.0-SNAPSHOT.jar");
        assertInstalled(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/"
                        + "maven-install-file-test-1.0-SNAPSHOT.pom");
        assertInstalled(localRepo, "org/apache/maven/test/maven-install-file-test/maven-metadata-local.xml");
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void existingPomNotOverwrittenWhenGeneratePomAuto(InstallFileMojo mojo) throws Exception {
        File pom = new File(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.pom");
        assertTrue(pom.getParentFile().isDirectory() || pom.getParentFile().mkdirs());
        Files.write(pom.toPath(), "test".getBytes(StandardCharsets.UTF_8));

        mojo.execute();

        assertInstalled(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/"
                        + "maven-install-file-test-1.0-SNAPSHOT.jar");
        assertInstalled(
                localRepo,
                "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/"
                        + "maven-install-file-test-1.0-SNAPSHOT.pom");
        assertEquals(
                "test",
                new String(Files.readAllBytes(pom.toPath()), StandardCharsets.UTF_8),
                "The existing POM must not be overwritten");
        assertInstalled(localRepo, "org/apache/maven/test/maven-install-file-test/maven-metadata-local.xml");
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "gid")
    @MojoParameter(name = "artifactId", value = "test")
    @MojoParameter(name = "version", value = "0.1")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void installFileIntoOverriddenLocalRepositoryNoChecksums(InstallFileMojo mojo) throws Exception {
        File overrideRepo =
                tempDir.resolve("target/local-repo").toAbsolutePath().toFile();
        setMojoField(mojo, "localRepositoryPath", overrideRepo);

        mojo.execute();

        String jarPath = "gid/test/0.1/test-0.1.jar";
        assertInstalled(overrideRepo, jarPath);
        InstallTestHelper.assertNoChecksum(overrideRepo, jarPath);
        assertFalse(new File(localRepo, jarPath).exists(), "The artifact should only be installed in " + overrideRepo);
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "gid")
    @MojoParameter(name = "artifactId", value = "test")
    @MojoParameter(name = "version", value = "0.1")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "generatePom", value = "true")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    void installFileIntoOverriddenLocalRepositoryWithGeneratedPomNoChecksums(InstallFileMojo mojo) throws Exception {
        File overrideRepo =
                tempDir.resolve("target/local-repo").toAbsolutePath().toFile();
        setMojoField(mojo, "localRepositoryPath", overrideRepo);

        mojo.execute();

        String jarPath = "gid/test/0.1/test-0.1.jar";
        String pomPath = "gid/test/0.1/test-0.1.pom";
        assertInstalled(overrideRepo, jarPath);
        assertInstalled(overrideRepo, pomPath);
        InstallTestHelper.assertNoChecksum(overrideRepo, jarPath);
        InstallTestHelper.assertNoChecksum(overrideRepo, pomPath);
        assertFalse(new File(localRepo, jarPath).exists(), "The artifact should only be installed in " + overrideRepo);
    }

    // -------------------------------------------------------------------------
    // Tests converted from the minstall-121 ITs (file/pomFile/packaging/extension)
    // -------------------------------------------------------------------------

    private static final class PackagingCase {
        private final String sourceFileName;
        private final String packaging;
        private final String extension;
        private final String artifactId;
        private final String artifactPath;
        private final String pomPath;
        private final String metadataPath;

        PackagingCase(
                String sourceFileName,
                String packaging,
                String extension,
                String artifactId,
                String artifactPath,
                String pomPath,
                String metadataPath) {
            this.sourceFileName = sourceFileName;
            this.packaging = packaging;
            this.extension = extension;
            this.artifactId = artifactId;
            this.artifactPath = artifactPath;
            this.pomPath = pomPath;
            this.metadataPath = metadataPath;
        }

        String sourceFileName() {
            return sourceFileName;
        }

        String packaging() {
            return packaging;
        }

        String extension() {
            return extension;
        }

        String artifactId() {
            return artifactId;
        }

        String artifactPath() {
            return artifactPath;
        }

        String pomPath() {
            return pomPath;
        }

        String metadataPath() {
            return metadataPath;
        }

        @Override
        public String toString() {
            return artifactId;
        }
    }

    private static Stream<PackagingCase> packagingCases() {
        String repoPrefix = "org/apache/maven/its/install/121/";
        return Stream.of(
                new PackagingCase(
                        "test-0.1.jar",
                        "maven-plugin",
                        null,
                        "test",
                        repoPrefix + "test/1.0/test-1.0.jar",
                        repoPrefix + "test/1.0/test-1.0.pom",
                        repoPrefix + "test/maven-metadata-local.xml"),
                new PackagingCase(
                        "test-0.1.jar",
                        "bundle",
                        "jar",
                        "maven-bundle",
                        repoPrefix + "maven-bundle/1.0/maven-bundle-1.0.jar",
                        repoPrefix + "maven-bundle/1.0/maven-bundle-1.0.pom",
                        repoPrefix + "maven-bundle/maven-metadata-local.xml"),
                new PackagingCase(
                        "test-0.1.jar",
                        "java-source",
                        null,
                        "test-java-source",
                        repoPrefix + "test-java-source/1.0/test-java-source-1.0-sources.jar",
                        repoPrefix + "test-java-source/1.0/test-java-source-1.0.pom",
                        repoPrefix + "test-java-source/maven-metadata-local.xml"),
                new PackagingCase(
                        "test-0.1.jar",
                        "maven-archetype",
                        "jar",
                        "maven-archetype",
                        repoPrefix + "maven-archetype/1.0/maven-archetype-1.0.jar",
                        repoPrefix + "maven-archetype/1.0/maven-archetype-1.0.pom",
                        repoPrefix + "maven-archetype/maven-metadata-local.xml"),
                new PackagingCase(
                        "test-0.1.tar.gz",
                        "war",
                        "tar.gz",
                        "test-targz",
                        repoPrefix + "test-targz/1.0/test-targz-1.0.tar.gz",
                        repoPrefix + "test-targz/1.0/test-targz-1.0.pom",
                        repoPrefix + "test-targz/maven-metadata-local.xml"),
                new PackagingCase(
                        "test-0.1.war",
                        "war",
                        null,
                        "test-war",
                        repoPrefix + "test-war/1.0/test-war-1.0.war",
                        repoPrefix + "test-war/1.0/test-war-1.0.pom",
                        repoPrefix + "test-war/maven-metadata-local.xml"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("packagingCases")
    @InjectMojo(goal = "install-file")
    void installFileWithPackagingAndExtension(PackagingCase testCase, InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);

        Path caseDir = tempDir.resolve(testCase.artifactId());
        Files.createDirectories(caseDir);
        File pomFile = caseDir.resolve("test-0.1.pom").toFile();
        Files.write(pomFile.toPath(), pomXmlWithParent(testCase.artifactId()).getBytes(StandardCharsets.UTF_8));
        File sourceFile = caseDir.resolve(testCase.sourceFileName()).toFile();
        Files.copy(
                MojoExtension.getTestFile("target/maven-install-test-1.0-SNAPSHOT.jar")
                        .toPath(),
                sourceFile.toPath());

        // The ITs relied on pomFile for all coordinates; class-level test coordinates must not leak in.
        setMojoField(mojo, "groupId", null);
        setMojoField(mojo, "artifactId", null);
        setMojoField(mojo, "version", null);
        setMojoField(mojo, "packaging", testCase.packaging());
        setMojoField(mojo, "extension", testCase.extension());
        setMojoField(mojo, "pomFile", pomFile);
        setMojoField(mojo, "file", sourceFile);

        mojo.execute();

        assertTrue(pomFile.isFile(), "Source POM should still exist: " + pomFile);
        assertTrue(sourceFile.isFile(), "Source file should still exist: " + sourceFile);
        assertInstalled(localRepo, testCase.artifactPath());
        assertInstalled(localRepo, testCase.pomPath());
        assertInstalled(localRepo, testCase.metadataPath());
        assertEquals(
                Files.readAllLines(pomFile.toPath()),
                Files.readAllLines(new File(localRepo, testCase.pomPath()).toPath()),
                "The installed POM must be the supplied pomFile, not a generated one");
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "packaging", value = "jar")
    void fileDoesNotExists(InstallFileMojo mojo) throws Exception {
        File missing = tempDir.resolve("does-not-exist.jar").toFile();
        setMojoField(mojo, "file", missing);

        MojoFailureException e = assertThrows(MojoFailureException.class, mojo::execute);
        assertTrue(e.getMessage().contains("does not exist"), e.getMessage());
        assertFalse(localRepo.exists(), "Nothing should be installed into " + localRepo);
    }

    private static String pomXmlWithParent(String artifactId) {
        return "<project xmlns=\"http://maven.apache.org/POM/4.0.0\""
                + " xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""
                + " xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0"
                + " http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n"
                + "  <modelVersion>4.0.0</modelVersion>\n"
                + "  <parent>\n"
                + "    <groupId>org.apache.maven.its.install.121</groupId>\n"
                + "    <artifactId>parent</artifactId>\n"
                + "    <version>1.0</version>\n"
                + "  </parent>\n"
                + "  <artifactId>"
                + artifactId
                + "</artifactId>\n"
                + "</project>\n";
    }

    private void assertInstalled(File repo, String path) {
        File installed = new File(repo, path);
        assertTrue(installed.isFile(), "Missing installed file: " + installed);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Path createJarWithEntries(String pomContent, String... entryNames) throws Exception {
        Path jar = Files.createTempFile("maven-install-file-test", ".jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            for (String entryName : entryNames) {
                jos.putNextEntry(new JarEntry(entryName));
                jos.write(pomContent.getBytes(StandardCharsets.UTF_8));
                jos.closeEntry();
            }
        }
        return jar;
    }

    private static Path createJarWithEntryBytes(byte[] pomContent, String entryName) throws Exception {
        Path jar = Files.createTempFile("maven-install-file-test", ".jar");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jar))) {
            jos.putNextEntry(new JarEntry(entryName));
            jos.write(pomContent);
            jos.closeEntry();
        }
        return jar;
    }

    private static String pomXml(String groupId, String artifactId, String version) {
        return "<project>" + "<modelVersion>4.0.0</modelVersion>"
                + "<groupId>" + groupId + "</groupId>"
                + "<artifactId>" + artifactId + "</artifactId>"
                + "<version>" + version + "</version>"
                + "<packaging>jar</packaging>"
                + "</project>";
    }

    private static void setMojoField(Object mojo, String fieldName, Object value) throws Exception {
        Field f = mojo.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(mojo, value);
    }

    private static Object getMojoField(Object mojo, String fieldName) throws Exception {
        Field f = mojo.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        return f.get(mojo);
    }
}

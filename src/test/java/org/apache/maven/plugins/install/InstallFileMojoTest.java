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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.LocalRepository;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.api.plugin.testing.stubs.ArtifactStub;
import org.apache.maven.api.plugin.testing.stubs.ProjectStub;
import org.apache.maven.api.plugin.testing.stubs.SessionMock;
import org.apache.maven.api.services.ArtifactInstaller;
import org.apache.maven.api.services.ArtifactInstallerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.getBasedir;
import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest
class InstallFileMojoTest {
    private static final String LOCAL_REPO = "target/local-repo";

    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private String classifier;
    private Path file;

    @Inject
    Session session;

    @Inject
    ArtifactManager artifactManager;

    @Inject
    ArtifactInstaller artifactInstaller;

    @BeforeEach
    void setUp() throws Exception {
        FileUtils.deleteDirectory(new File(getBasedir() + "/" + LOCAL_REPO));
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void installFileTestEnvironment(InstallFileMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void basicInstallFile(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);

        mojo.execute();

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Set<Artifact> artifacts = new HashSet<>(request.getArtifacts());
        Artifact pom = getArtifact(null, "pom");
        Artifact jar = getArtifact(null, "jar");
        assertEquals(new HashSet<>(Arrays.asList(pom, jar)), artifacts);
        assertFileExists(artifactManager.getPath(jar).orElse(null));
        assertFileExists(artifactManager.getPath(jar).orElse(null));
        assertEquals(
                LOCAL_REPO,
                request.getSession().getLocalRepository().getPath().toString().replace(File.separator, "/"));
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${project.basedir}/target/test-classes/unit/file-does-not-exist.jar")
    void fileDoesNotExists(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);

        assertThrows(MojoException.class, mojo::execute);
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "classifier", value = "sources")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void installFileWithClassifier(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);
        assertNotNull(classifier);

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Set<Artifact> artifacts = new HashSet<>(request.getArtifacts());
        Artifact pom = getArtifact(null, "pom");
        Artifact sources = getArtifact("sources", "jar");
        assertEquals(new HashSet<>(Arrays.asList(pom, sources)), artifacts);
        // pom file does not exist, as it should have been deleted after the installation
        assertTrue(artifactManager.getPath(pom).isEmpty());
        assertFileExists(artifactManager.getPath(sources).get());
        assertEquals(
                LOCAL_REPO,
                request.getSession().getLocalRepository().getPath().toString().replace(File.separator, "/"));
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    @MojoParameter(name = "generatePom", value = "true")
    void installFileWithGeneratePom(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);
        assertTrue((Boolean) getVariableValueFromObject(mojo, "generatePom"));

        AtomicReference<Model> model = new AtomicReference<>();
        ArtifactInstallerRequest request = execute(mojo, air -> model.set(readModel(getArtifact(null, "pom"))));

        assertNotNull(request);
        Set<Artifact> artifacts = new HashSet<>(request.getArtifacts());
        Artifact pom = getArtifact(null, "pom");
        Artifact jar = getArtifact(null, "jar");
        assertEquals(new HashSet<>(Arrays.asList(pom, jar)), artifacts);
        assertEquals("4.0.0", model.get().getModelVersion());
        assertEquals(getVariableValueFromObject(mojo, "groupId"), model.get().getGroupId());
        assertEquals(artifactId, model.get().getArtifactId());
        assertEquals(version, model.get().getVersion());
        assertNotNull(artifactManager.getPath(jar).orElse(null));
        assertEquals(
                LOCAL_REPO,
                request.getSession().getLocalRepository().getPath().toString().replace(File.separator, "/"));
    }

    private Model readModel(Artifact pom) {
        try {
            Path pomPath = artifactManager.getPath(pom).orElse(null);
            assertNotNull(pomPath);
            return session.getService(ModelXmlFactory.class).read(pomPath);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    @MojoParameter(name = "pomFile", value = "${project.basedir}/src/test/resources/unit/pom.xml")
    void installFileWithPomFile(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);
        Path pomFile = (Path) getVariableValueFromObject(mojo, "pomFile");

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Set<Artifact> artifacts = new HashSet<>(request.getArtifacts());
        Artifact pom = getArtifact(null, "pom");
        Artifact jar = getArtifact(null, "jar");
        assertEquals(new HashSet<>(Arrays.asList(pom, jar)), artifacts);
        assertEquals(pomFile, artifactManager.getPath(pom).orElse(null));
        assertNotNull(artifactManager.getPath(jar).orElse(null));
        assertEquals(
                LOCAL_REPO,
                request.getSession().getLocalRepository().getPath().toString().replace(File.separator, "/"));
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "pom")
    @MojoParameter(name = "file", value = "${project.basedir}/target/test-classes/unit/pom.xml")
    void installFileWithPomAsPackaging(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);
        assertTrue(Files.exists(file));
        assertEquals("pom", packaging);

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Set<Artifact> artifacts = new HashSet<>(request.getArtifacts());
        Artifact pom = getArtifact(null, "pom");
        assertEquals(Collections.singleton(pom), artifacts);
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    @MojoParameter(name = "pomFile", value = "${project.basedir}/target/test-classes/unit/pom.xml")
    void installFile(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Set<Artifact> artifacts = new HashSet<>(request.getArtifacts());
        Artifact pom = getArtifact(null, "pom");
        Artifact jar = getArtifact(null, "jar");
        assertEquals(new HashSet<>(Arrays.asList(pom, jar)), artifacts);
        assertEquals(
                LOCAL_REPO,
                request.getSession().getLocalRepository().getPath().toString().replace(File.separator, "/"));
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void adoptsCoordinatesFromConsistentEmbeddedPom(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        Path jar = createJarWithEntries(
                pomXml("org.example", "embedded-lib", "1.0"), "META-INF/maven/org.example/embedded-lib/pom.xml");
        setVariableValueToObject(mojo, "file", jar);

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        assertEquals("org.example", getVariableValueFromObject(mojo, "groupId"));
        assertEquals("embedded-lib", getVariableValueFromObject(mojo, "artifactId"));
        assertEquals("1.0", getVariableValueFromObject(mojo, "version"));
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsEmbeddedPomWithMismatchedEntryPath(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        // decoy entry path does not match the coordinates the embedded POM declares
        Path jar = createJarWithEntries(
                pomXml("org.apache.maven.plugins", "maven-clean-plugin", "3.4.0"),
                "META-INF/maven/org.evil/decoy/pom.xml");
        setVariableValueToObject(mojo, "file", jar);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("entry path"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsJarWithMultipleEmbeddedPoms(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        Path jar = createJarWithEntries(
                pomXml("org.example", "embedded-lib", "1.0"),
                "META-INF/maven/org.example/embedded-lib/pom.xml",
                "META-INF/maven/org.other/other-lib/pom.xml");
        setVariableValueToObject(mojo, "file", jar);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("POM entries"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar!")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void invalidPackagingRejected(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void embeddedPomIgnoredWhenFullCoordinatesSupplied(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);
        // hostile embedded POM carrying a foreign GAV and an injected dependency
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
        setVariableValueToObject(mojo, "file", jar);

        AtomicReference<Model> model = new AtomicReference<>();
        ArtifactInstallerRequest request = execute(mojo, air -> model.set(readModel(getArtifact(null, "pom"))));

        assertNotNull(request);
        // the installed POM is the generated minimal POM at the CLI coordinates, not the embedded one
        assertEquals("org.apache.maven.test", model.get().getGroupId());
        assertEquals("maven-install-file-test", model.get().getArtifactId());
        assertEquals("1.0-SNAPSHOT", model.get().getVersion());
        assertTrue(model.get().getDependencies().isEmpty());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void mismatchedEmbeddedPomRejectedWhenPackagingOmitted(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        // packaging omitted, so the embedded POM is consulted; the attacker POM is internally
        // consistent (entry path matches its own declared GAV) but disagrees with the supplied
        // coordinates — it must not be installed verbatim at those coordinates
        Path jar =
                createJarWithEntries(pomXml("com.evil", "injected", "9.9"), "META-INF/maven/com.evil/injected/pom.xml");
        setVariableValueToObject(mojo, "file", jar);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("does not match the supplied coordinates"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void matchingEmbeddedPomAcceptedWhenPackagingOmitted(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        // packaging omitted; the embedded POM agrees with every supplied coordinate, so it may be
        // used and contribute the missing packaging
        Path jar = createJarWithEntries(
                pomXml("org.apache.maven.test", "maven-install-file-test", "1.0-SNAPSHOT"),
                "META-INF/maven/org.apache.maven.test/maven-install-file-test/pom.xml");
        setVariableValueToObject(mojo, "file", jar);

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        assertEquals("jar", getVariableValueFromObject(mojo, "packaging"));
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsEmbeddedPomWithDoctype(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        String xxePom = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE project [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + pomXml("org.example", "embedded-lib", "1.0");
        Path jar = createJarWithEntries(xxePom, "META-INF/maven/org.example/embedded-lib/pom.xml");
        setVariableValueToObject(mojo, "file", jar);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("DOCTYPE"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsEmbeddedPomWithDoctypeUtf16Le(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        String xxePom = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE project [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + pomXml("org.example", "embedded-lib", "1.0");
        // BOM-prefixed UTF-16LE: interleaved NUL bytes defeat a single-byte substring scan
        byte[] bytes = ("\uFEFF" + xxePom).getBytes(StandardCharsets.UTF_16LE);
        Path jar = createJarWithEntryBytes(bytes, "META-INF/maven/org.example/embedded-lib/pom.xml");
        setVariableValueToObject(mojo, "file", jar);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("DOCTYPE"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsEmbeddedPomWithDoctypeUtf16Be(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        String xxePom = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE project [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + pomXml("org.example", "embedded-lib", "1.0");
        // BOM-prefixed UTF-16BE
        byte[] bytes = ("\uFEFF" + xxePom).getBytes(StandardCharsets.UTF_16BE);
        Path jar = createJarWithEntryBytes(bytes, "META-INF/maven/org.example/embedded-lib/pom.xml");
        setVariableValueToObject(mojo, "file", jar);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("DOCTYPE"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsEmbeddedPomWithDoctypeEbcdicIbm500(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        String xxePom = "<?xml version=\"1.0\" encoding=\"IBM500\"?>"
                + "<!DOCTYPE project [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + pomXml("org.example", "embedded-lib", "1.0");
        // IBM500 and IBM037 disagree on the EBCDIC variant byte for '!': a screen that decodes the
        // whole document as IBM037 sees "<|DOCTYPE" and misses the DTD. The declared code page must win.
        byte[] bytes = xxePom.getBytes(java.nio.charset.Charset.forName("IBM500"));
        Path jar = createJarWithEntryBytes(bytes, "META-INF/maven/org.example/embedded-lib/pom.xml");
        setVariableValueToObject(mojo, "file", jar);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("DOCTYPE"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void rejectsEmbeddedPomWithDeclPaddedPastSniffPrefix(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        // Declaration whitespace is unbounded: pad the ASCII declaration so 'encoding="IBM037"' and
        // '?>' sit past the 1024-byte sniff prefix, then append an IBM037 body carrying the DOCTYPE.
        // A fail-open UTF-8 default screens the wrong charset and misses the DTD that a
        // declaration-honoring parser would decode; the screen must fail closed instead.
        StringBuilder decl = new StringBuilder("<?xml version=\"1.0\"");
        for (int i = 0; i < 1100; i++) {
            decl.append(' ');
        }
        decl.append("encoding=\"IBM037\"?>");
        String body = "<!DOCTYPE project [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + pomXml("org.example", "embedded-lib", "1.0");
        byte[] declBytes = decl.toString().getBytes(StandardCharsets.US_ASCII);
        byte[] bodyBytes = body.getBytes(java.nio.charset.Charset.forName("IBM037"));
        byte[] bytes = new byte[declBytes.length + bodyBytes.length];
        System.arraycopy(declBytes, 0, bytes, 0, declBytes.length);
        System.arraycopy(bodyBytes, 0, bytes, declBytes.length, bodyBytes.length);
        Path jar = createJarWithEntryBytes(bytes, "META-INF/maven/org.example/embedded-lib/pom.xml");
        setVariableValueToObject(mojo, "file", jar);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("DOCTYPE"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = ".tmp.evil")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void groupIdWithLeadingDotRejected(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org..evil")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void groupIdWithConsecutiveDotsRejected(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "..")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void dotDotArtifactIdRejected(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "..")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void dotDotVersionRejected(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertTrue(e.getMessage().contains("not valid"), e.getMessage());
    }

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

    private void assignValuesForParameter(Object obj) throws Exception {
        this.groupId = (String) getVariableValueFromObject(obj, "groupId");
        this.artifactId = (String) getVariableValueFromObject(obj, "artifactId");
        this.version = (String) getVariableValueFromObject(obj, "version");
        this.packaging = (String) getVariableValueFromObject(obj, "packaging");
        this.classifier = (String) getVariableValueFromObject(obj, "classifier");
        this.file = (Path) getVariableValueFromObject(obj, "file");
    }

    private ArtifactStub getArtifact(String classifier, String extension) {
        return new ArtifactStub(groupId, artifactId, classifier != null ? classifier : "", version, extension);
    }

    private ArtifactInstallerRequest execute(InstallFileMojo mojo) {
        return execute(mojo, null);
    }

    private ArtifactInstallerRequest execute(InstallFileMojo mojo, Consumer<ArtifactInstallerRequest> consumer) {
        AtomicReference<ArtifactInstallerRequest> request = new AtomicReference<>();
        doAnswer(iom -> {
                    ArtifactInstallerRequest req = iom.getArgument(0, ArtifactInstallerRequest.class);
                    request.set(req);
                    if (consumer != null) {
                        consumer.accept(req);
                    }
                    return null;
                })
                .when(artifactInstaller)
                .install(any(ArtifactInstallerRequest.class));
        mojo.execute();
        return request.get();
    }

    private void assertFileExists(Path path) {
        assertTrue(path != null && Files.exists(path), () -> path + " should exists");
    }

    private void assertFileNotExists(Path path) {
        assertFalse(path != null && Files.exists(path), () -> path + " should not exists");
    }

    @Provides
    @Singleton
    @Priority(10)
    @SuppressWarnings("unused")
    private static Session createMavenSession() {
        Session session = SessionMock.getMockSession(LOCAL_REPO);
        when(session.withLocalRepository(any())).thenAnswer(iom -> {
            LocalRepository localRepository = iom.getArgument(0, LocalRepository.class);
            Session mockSession = SessionMock.getMockSession(localRepository);
            when(mockSession.getService(ArtifactInstaller.class))
                    .thenAnswer(iom2 -> session.getService(ArtifactInstaller.class));
            return mockSession;
        });
        return session;
    }

    @Provides
    private static Project createProject() {
        ProjectStub project = new ProjectStub();
        project.setBasedir(Paths.get(getBasedir()));
        return project;
    }
}

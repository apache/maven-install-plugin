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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.LocalRepository;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.api.plugin.testing.stubs.ProjectStub;
import org.apache.maven.api.plugin.testing.stubs.SessionMock;
import org.apache.maven.api.services.ArtifactInstaller;
import org.apache.maven.api.services.ArtifactInstallerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderException;
import org.apache.maven.testing.plugin.stubs.ArtifactStub;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.apache.maven.api.plugin.testing.MojoExtension.getBasedir;
import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest
class InstallFileMojoTest {
    private static final String LOCAL_REPO = "target/local-repo";
    private static final String OVERRIDE_REPO = "target/local-repo-override";

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

    // -------------------------------------------------------------------------------------------------------
    // Conversions of the src/it integration tests that only exercised install:install-file mojo behaviour.
    // The real ArtifactInstaller is mocked by the harness, so "installed" is asserted as the set of
    // artifacts handed to it (plus the generated POM model captured while the install call is in flight).
    // Checksum files are produced by Maven's ArtifactInstaller, never by this mojo; at this level we
    // assert that no checksum artifact is handed to the installer and that none is left in the repo.
    // -------------------------------------------------------------------------------------------------------

    /** Converted from {@code src/it/gav-validation} (invalid coordinates, test-invalid.properties). */
    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "invalid~group")
    @MojoParameter(name = "artifactId", value = "invalid/artifactId")
    @MojoParameter(name = "version", value = "1+")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void invalidGavRejectedAndNothingInstalled(InstallFileMojo mojo) {
        assertNotNull(mojo);

        MojoException e = executeExpectingFailure(mojo);

        assertTrue(e.getMessage().contains("The artifact information is not valid"), e.getMessage());
    }

    /** Converted from {@code src/it/gav-validation} (missing coordinates, test-missing.properties). */
    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "groupId")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void missingCoordinatesRejectedAndNothingInstalled(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        // A jar with no embedded POM keeps the coordinates genuinely incomplete.
        Path plainJar = createJarWithEntryBytes(
                "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8), "META-INF/MANIFEST.MF");
        setVariableValueToObject(mojo, "file", plainJar);

        MojoException e = executeExpectingFailure(mojo);

        assertTrue(e.getMessage().contains("The artifact information is incomplete"), e.getMessage());
    }

    /** Converted from {@code src/it/generate-pom-auto-1}: with the default generatePom, a jar and a POM are installed. */
    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.its.install.gpa1")
    @MojoParameter(name = "artifactId", value = "test")
    @MojoParameter(name = "version", value = "0.1")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void generatePomAutoInstallsJarAndPom(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);

        AtomicReference<Model> model = new AtomicReference<>();
        ArtifactInstallerRequest request =
                execute(mojo, air -> model.set(readModelFrom(air, getArtifact(null, "pom"))));

        assertNotNull(request);
        Artifact pom = getArtifact(null, "pom");
        Artifact jar = getArtifact(null, "jar");
        assertEquals(new HashSet<>(Arrays.asList(pom, jar)), new HashSet<>(request.getArtifacts()));
        assertFileExists(artifactManager.getPath(jar).orElse(null));
        assertEquals("4.0.0", model.get().getModelVersion());
        assertEquals(groupId, model.get().getGroupId());
        assertEquals(artifactId, model.get().getArtifactId());
        assertEquals(version, model.get().getVersion());
        InstallTestHelper.assertNoChecksumFiles(Paths.get(getBasedir(), LOCAL_REPO));
    }

    /** Converted from {@code src/it/generate-pom-auto-2}: a POM already in the repo is never overwritten. */
    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.its.install.gpa2")
    @MojoParameter(name = "artifactId", value = "test")
    @MojoParameter(name = "version", value = "0.1")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void existingPomNotOverwrittenWhenGeneratePomAuto(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);

        Path existingPom =
                Paths.get(getBasedir(), LOCAL_REPO, "org/apache/maven/its/install/gpa2/test/0.1/test-0.1.pom");
        Files.createDirectories(existingPom.getParent());
        Files.writeString(existingPom, "test");

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Artifact jar = getArtifact(null, "jar");
        assertEquals(Collections.singleton(jar), new HashSet<>(request.getArtifacts()));
        assertFileExists(artifactManager.getPath(jar).orElse(null));
        assertEquals("test", Files.readString(existingPom), "existing POM must keep its original 4-byte content");
        InstallTestHelper.assertNoChecksumFiles(Paths.get(getBasedir(), LOCAL_REPO));
    }

    /** Converted from {@code src/it/local-repo-override-with-checksum}: install into a custom local repository. */
    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "gid")
    @MojoParameter(name = "artifactId", value = "test")
    @MojoParameter(name = "version", value = "0.1")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    @MojoParameter(name = "localRepositoryPath", value = "target/local-repo-override")
    void installFileIntoOverriddenLocalRepositoryNoChecksums(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Artifact jar = getArtifact(null, "jar");
        assertTrue(request.getArtifacts().contains(jar), "the jar must be installed into the overridden repository");
        assertTrue(request.getArtifacts().stream()
                .noneMatch(a -> "md5".equals(a.getExtension()) || "sha1".equals(a.getExtension())));
        assertFileExistsInRequest(request, jar);
        assertEquals(
                Paths.get(getBasedir(), OVERRIDE_REPO).toString().replace(File.separator, "/"),
                request.getSession().getLocalRepository().getPath().toString().replace(File.separator, "/"));
        InstallTestHelper.assertNoChecksumFiles(Paths.get(getBasedir(), OVERRIDE_REPO));
    }

    /**
     * Converted from {@code src/it/local-repo-override-with-checksum-generatePom}: the generated POM is installed
     * into the custom local repository too, and neither jar nor POM gets a checksum file.
     */
    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "gid")
    @MojoParameter(name = "artifactId", value = "test")
    @MojoParameter(name = "version", value = "0.1")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    @MojoParameter(name = "generatePom", value = "true")
    @MojoParameter(name = "localRepositoryPath", value = "target/local-repo-override")
    void installFileIntoOverriddenLocalRepositoryWithGeneratedPomNoChecksums(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);

        AtomicReference<Model> model = new AtomicReference<>();
        ArtifactInstallerRequest request =
                execute(mojo, air -> model.set(readModelFrom(air, getArtifact(null, "pom"))));

        assertNotNull(request);
        Artifact pom = getArtifact(null, "pom");
        Artifact jar = getArtifact(null, "jar");
        assertEquals(new HashSet<>(Arrays.asList(pom, jar)), new HashSet<>(request.getArtifacts()));
        assertFileExistsInRequest(request, jar);
        assertEquals(
                Paths.get(getBasedir(), OVERRIDE_REPO).toString().replace(File.separator, "/"),
                request.getSession().getLocalRepository().getPath().toString().replace(File.separator, "/"));
        assertEquals(groupId, model.get().getGroupId());
        assertEquals(artifactId, model.get().getArtifactId());
        assertEquals(version, model.get().getVersion());
        InstallTestHelper.assertNoChecksumFiles(Paths.get(getBasedir(), OVERRIDE_REPO));
    }

    /**
     * The six converted install-file ITs supplied only {@code file}, {@code pomFile} and
     * {@code packaging} through {@code test.properties}; groupId/version were adopted from the supplied POM's
     * parent and artifactId from the POM itself. Each IT asserted that these files existed in the local repository:
     *
     * <pre>
     *   org/apache/maven/its/install/121/&lt;artifactId&gt;/maven-metadata-local.xml
     *   org/apache/maven/its/install/121/&lt;artifactId&gt;/1.0/&lt;artifactId&gt;-1.0.&lt;extension&gt;
     *   org/apache/maven/its/install/121/&lt;artifactId&gt;/1.0/&lt;artifactId&gt;-1.0.pom
     * </pre>
     *
     * plus that the source {@code test-0.1.pom} and input file still existed in the working directory. The metadata
     * file is written by the real ArtifactInstaller (mocked by this harness), so it is the one IT assertion that
     * cannot be made here; the coordinates that determine its path are asserted below.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("packagingCases")
    void installFileUsesPackagingForArtifactExtension(PackagingCase testCase) throws Exception {
        // The MojoTest harness's ParameterResolver cannot be combined with @ParameterizedTest (both claim the
        // mojo parameter), so wire the mojo with the same injected services the harness would supply.
        InstallFileMojo mojo = new InstallFileMojo();
        setVariableValueToObject(mojo, "log", mock(Log.class));
        setVariableValueToObject(mojo, "session", session);

        Path fixtureDir = Paths.get(getBasedir(), "target/test-classes/unit/artifact-extension", testCase.directory());
        Path file = fixtureDir.resolve(testCase.fileName());
        Path pomFile = fixtureDir.resolve("test-0.1.pom");
        assertTrue(Files.isRegularFile(file), () -> "missing IT fixture " + file);
        assertTrue(Files.isRegularFile(pomFile), () -> "missing IT fixture " + pomFile);

        // Reproduce the IT inputs: only file/pomFile/packaging were supplied; the coordinates come from the POM.
        setVariableValueToObject(mojo, "file", file);
        setVariableValueToObject(mojo, "pomFile", pomFile);
        setVariableValueToObject(mojo, "packaging", testCase.packaging());
        if (testCase.classifier() != null) {
            // A real Maven ArtifactTypeRegistry maps java-source to classifier "sources"; the MojoTest harness's
            // ArtifactFactory stub does not, so supply it to preserve the IT's asserted -sources.jar coordinates.
            setVariableValueToObject(mojo, "classifier", testCase.classifier());
        }

        this.groupId = "org.apache.maven.its.install.121";
        this.artifactId = testCase.artifactId();
        this.version = "1.0";

        AtomicReference<Model> model = new AtomicReference<>();
        ArtifactInstallerRequest request =
                execute(mojo, air -> model.set(readModelFrom(air, getArtifact(null, "pom"))));

        assertNotNull(request);
        Artifact main = getArtifact(testCase.classifier(), testCase.extension());
        Artifact pom = getArtifact(null, "pom");
        assertEquals(new HashSet<>(Arrays.asList(main, pom)), new HashSet<>(request.getArtifacts()));

        String groupPath = "org/apache/maven/its/install/121/" + testCase.artifactId();
        String versionPath = groupPath + "/1.0/" + testCase.artifactId() + "-1.0";
        assertEquals(
                versionPath
                        + (testCase.classifier() != null ? "-" + testCase.classifier() : "")
                        + "." + testCase.extension(),
                repoRelativePath(session.getPathForLocalArtifact(main)));
        assertEquals(versionPath + ".pom", repoRelativePath(session.getPathForLocalArtifact(pom)));

        // The POM installed is the one supplied by the IT, and its coordinates are the ones used for install.
        assertEquals(pomFile, artifactManager.getPath(pom).orElse(null));
        assertEquals(testCase.artifactId(), model.get().getArtifactId());
        assertEquals("org.apache.maven.its.install.121", model.get().getParent().getGroupId());
        assertEquals("1.0", model.get().getParent().getVersion());
        assertEquals(groupId, getVariableValueFromObject(mojo, "groupId"));
        assertEquals(artifactId, getVariableValueFromObject(mojo, "artifactId"));
        assertEquals(version, getVariableValueFromObject(mojo, "version"));

        assertFileExists(artifactManager.getPath(main).orElse(null));
        assertEquals(
                LOCAL_REPO,
                request.getSession().getLocalRepository().getPath().toString().replace(File.separator, "/"));
        InstallTestHelper.assertNoChecksumFiles(Paths.get(getBasedir(), LOCAL_REPO));
    }

    static Stream<PackagingCase> packagingCases() {
        return Stream.of(
                new PackagingCase("maven-plugin", "test", "test-0.1.jar", "maven-plugin", "test", null, "jar"),
                new PackagingCase("bundle", "bundle", "test-0.1.jar", "bundle", "maven-bundle", null, "jar"),
                new PackagingCase(
                        "java-source",
                        "java-source",
                        "test-0.1.jar",
                        "java-source",
                        "test-java-source",
                        "sources",
                        "jar"),
                new PackagingCase(
                        "maven-archetype",
                        "maven-archetype",
                        "test-0.1.jar",
                        "maven-archetype",
                        "maven-archetype",
                        null,
                        "jar"),
                new PackagingCase("targz", "targz", "test-0.1.tar.gz", "war", "test-targz", null, "tar.gz"),
                new PackagingCase("war", "war", "test-0.1.war", "war", "test-war", null, "war"));
    }

    record PackagingCase(
            String name,
            String directory,
            String fileName,
            String packaging,
            String artifactId,
            String classifier,
            String extension) {}

    /**
     * Converted from {@code src/it/install-file-generated-pom}: install-file is given only {@code file} and
     * {@code pomFile}; groupId/artifactId/version are inherited from the supplied POM's parent. The IT asserted
     * that the POM and JAR land at the inherited 1.0 coordinates, that the group-level metadata exists, and that
     * the original {@code pomFile} is still on disk. The harness mocks the real {@link ArtifactInstaller} (which
     * writes the metadata), so the metadata path is expressed through the install request's coordinates.
     */
    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    void installFileWithPomFileInheritsCoordinatesAndKeepsSourcePom(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        Path pomFile = Files.createTempFile("install-file-generated-pom", ".pom");
        Files.writeString(
                pomFile,
                "<project>"
                        + "<modelVersion>4.0.0</modelVersion>"
                        + "<parent>"
                        + "<groupId>org.apache.maven.its.install.55</groupId>"
                        + "<artifactId>parent</artifactId>"
                        + "<version>1.0</version>"
                        + "</parent>"
                        + "<artifactId>test</artifactId>"
                        + "</project>");
        setVariableValueToObject(mojo, "pomFile", pomFile);

        this.groupId = "org.apache.maven.its.install.55";
        this.artifactId = "test";
        this.version = "1.0";

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Artifact pom = getArtifact(null, "pom");
        Artifact jar = getArtifact(null, "jar");
        assertEquals(new HashSet<>(Arrays.asList(pom, jar)), new HashSet<>(request.getArtifacts()));
        assertEquals(pomFile, artifactManager.getPath(pom).orElse(null));
        assertFileExists(artifactManager.getPath(jar).orElse(null));
        assertTrue(Files.isRegularFile(pomFile), "the source pomFile must still exist after installation");
        assertGroupMetadataCoordinates(request, "org/apache/maven/its/install/55/test");
        InstallTestHelper.assertNoChecksumFiles(Paths.get(getBasedir(), LOCAL_REPO));
    }

    /**
     * Converted from {@code src/it/jar-sources-javadoc}: install-file with a JAR plus {@code sources} and
     * {@code javadoc} attachments installs all four files (POM, JAR, sources, javadoc) and no checksum files.
     * The harness mocks the real {@link ArtifactInstaller} (which writes metadata), so the group-level metadata
     * path is expressed through the install request's coordinates.
     */
    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.its.install.jsj")
    @MojoParameter(name = "artifactId", value = "test")
    @MojoParameter(name = "version", value = "0.1")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(
            name = "file",
            value = "${project.basedir}/target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar")
    @MojoParameter(
            name = "sources",
            value = "${project.basedir}/target/test-classes/unit/attached-artifact-test-1.0-SNAPSHOT.jar")
    @MojoParameter(
            name = "javadoc",
            value = "${project.basedir}/target/test-classes/unit/attached-artifact-test-1.0-SNAPSHOT.jar")
    void installFileWithSourcesAndJavadocInstallsAllFourFilesWithoutChecksums(InstallFileMojo mojo) throws Exception {
        assertNotNull(mojo);
        assignValuesForParameter(mojo);

        Artifact pom = getArtifact(null, "pom");
        Artifact jar = getArtifact(null, "jar");
        Artifact sources = getArtifact("sources", "jar");
        Artifact javadoc = getArtifact("javadoc", "jar");
        // The generated POM is a temporary file on the harness's in-memory file system that the mojo deletes
        // (and clears the path for) in its finally block, so capture its content while the install call is in
        // flight; the real jar/sources/javadoc inputs are checked as ordinary files below.
        Map<Artifact, Path> installedPaths = new HashMap<>();
        AtomicReference<Model> generatedPom = new AtomicReference<>();
        ArtifactInstallerRequest request = execute(mojo, air -> {
            ArtifactManager requestArtifactManager = air.getSession().getService(ArtifactManager.class);
            for (Artifact artifact : Arrays.asList(jar, sources, javadoc)) {
                installedPaths.put(
                        artifact, requestArtifactManager.getPath(artifact).orElse(null));
            }
            generatedPom.set(readModelFrom(air, pom));
        });

        assertNotNull(request);
        assertEquals(new HashSet<>(Arrays.asList(pom, jar, sources, javadoc)), new HashSet<>(request.getArtifacts()));
        for (Artifact artifact : Arrays.asList(jar, sources, javadoc)) {
            Path path = installedPaths.get(artifact);
            assertTrue(
                    path != null && Files.exists(path),
                    () -> artifact.key() + " should have an existing file, got " + path);
        }
        assertEquals("4.0.0", generatedPom.get().getModelVersion());
        assertEquals(groupId, generatedPom.get().getGroupId());
        assertEquals(artifactId, generatedPom.get().getArtifactId());
        assertEquals(version, generatedPom.get().getVersion());
        assertEquals(
                "org/apache/maven/its/install/jsj/test/0.1/test-0.1.jar",
                repoRelativePath(session.getPathForLocalArtifact(jar)));
        assertEquals(
                "org/apache/maven/its/install/jsj/test/0.1/test-0.1.pom",
                repoRelativePath(session.getPathForLocalArtifact(pom)));
        assertEquals(
                "org/apache/maven/its/install/jsj/test/0.1/test-0.1-sources.jar",
                repoRelativePath(session.getPathForLocalArtifact(sources)));
        assertEquals(
                "org/apache/maven/its/install/jsj/test/0.1/test-0.1-javadoc.jar",
                repoRelativePath(session.getPathForLocalArtifact(javadoc)));
        assertTrue(request.getArtifacts().stream()
                .noneMatch(a -> "md5".equals(a.getExtension()) || "sha1".equals(a.getExtension())));
        assertGroupMetadataCoordinates(request, "org/apache/maven/its/install/jsj/test");
        InstallTestHelper.assertNoChecksumFiles(Paths.get(getBasedir(), LOCAL_REPO));
    }

    private void assertGroupMetadataCoordinates(ArtifactInstallerRequest request, String groupArtifactPath) {
        Artifact pom = request.getArtifacts().stream()
                .filter(a -> "pom".equals(a.getExtension()))
                .findFirst()
                .orElseThrow();
        Path pomPath = request.getSession().getPathForLocalArtifact(pom);
        assertEquals(
                groupArtifactPath + "/maven-metadata-local.xml",
                InstallTestHelper.repoRelativePath(InstallTestHelper.groupLevelMetadataPath(pomPath), LOCAL_REPO));
    }

    private static String repoRelativePath(Path path) {
        // The harness returns relative Paths from a different FileSystem provider than Paths.get, so compare strings.
        String prefix = LOCAL_REPO + "/";
        String value = path.toString().replace(File.separator, "/");
        return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
    }

    private void assertFileExistsInRequest(ArtifactInstallerRequest request, Artifact artifact) {
        ArtifactManager requestArtifactManager = request.getSession().getService(ArtifactManager.class);
        assertFileExists(requestArtifactManager.getPath(artifact).orElse(null));
    }

    private Model readModelFrom(ArtifactInstallerRequest request, Artifact pom) {
        try {
            ArtifactManager requestArtifactManager = request.getSession().getService(ArtifactManager.class);
            Path pomPath = requestArtifactManager.getPath(pom).orElse(null);
            assertNotNull(pomPath);
            return session.getService(ModelXmlFactory.class).read(pomPath);
        } catch (XmlReaderException e) {
            throw new IllegalStateException(e);
        }
    }

    private MojoException executeExpectingFailure(InstallFileMojo mojo) {
        AtomicReference<ArtifactInstallerRequest> request = new AtomicReference<>();
        doAnswer(iom -> {
                    request.set(iom.getArgument(0, ArtifactInstallerRequest.class));
                    return null;
                })
                .when(artifactInstaller)
                .install(any(ArtifactInstallerRequest.class));
        MojoException e = assertThrows(MojoException.class, mojo::execute);
        assertNull(request.get(), "nothing should be installed on validation failure");
        return e;
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
        when(session.createLocalRepository(any(Path.class))).thenAnswer(iom -> {
            Path path = iom.getArgument(0, Path.class);
            LocalRepository localRepository = mock(LocalRepository.class);
            when(localRepository.getPath()).thenReturn(path);
            when(localRepository.getId()).thenReturn("local");
            return localRepository;
        });
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

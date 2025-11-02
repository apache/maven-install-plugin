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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.getBasedir;
import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
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
public class InstallFileMojoTest {
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
    public void setUp() throws Exception {
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
    public void testInstallFileTestEnvironment(InstallFileMojo mojo) {
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
    public void testBasicInstallFile(InstallFileMojo mojo) throws Exception {
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
    public void testFileDoesNotExists(InstallFileMojo mojo) throws Exception {
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
    public void testInstallFileWithClassifier(InstallFileMojo mojo) throws Exception {
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
    public void testInstallFileWithGeneratePom(InstallFileMojo mojo) throws Exception {
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
    public void testInstallFileWithPomFile(InstallFileMojo mojo) throws Exception {
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
    public void testInstallFileWithPomAsPackaging(InstallFileMojo mojo) throws Exception {
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
    public void testInstallFile(InstallFileMojo mojo) throws Exception {
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

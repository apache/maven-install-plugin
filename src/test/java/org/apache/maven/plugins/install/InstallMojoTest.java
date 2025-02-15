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
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.MojoExecution;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.plugin.Mojo;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.api.plugin.testing.stubs.MojoExecutionStub;
import org.apache.maven.api.plugin.testing.stubs.ProducedArtifactStub;
import org.apache.maven.api.plugin.testing.stubs.ProjectStub;
import org.apache.maven.api.plugin.testing.stubs.SessionMock;
import org.apache.maven.api.services.ArtifactInstaller;
import org.apache.maven.api.services.ArtifactInstallerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.internal.impl.InternalSession;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.getBasedir;
import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@MojoTest
public class InstallMojoTest {

    private static final String LOCAL_REPO = "target/local-repo/";

    @Inject
    InternalSession session;

    @Inject
    ArtifactInstaller artifactInstaller;

    @Inject
    ArtifactManager artifactManager;

    @Inject
    ProjectManager projectManager;

    @BeforeEach
    public void setUp() throws Exception {
        FileUtils.deleteDirectory(new File(getBasedir() + "/" + LOCAL_REPO));
    }

    @Test
    @InjectMojo(goal = "install")
    public void testInstallTestEnvironment(InstallMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    @InjectMojo(goal = "install")
    @MojoParameter(name = "installAtEnd", value = "false")
    public void testBasicInstall(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        artifactManager.setPath(
                project.getMainArtifact().get(),
                Paths.get(getBasedir(), "target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar"));

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Collection<ProducedArtifact> artifacts = request.getArtifacts();
        assertEquals(
                Arrays.asList(
                        "org.apache.maven.test:maven-install-test:pom:1.0-SNAPSHOT",
                        "org.apache.maven.test:maven-install-test:jar:1.0-SNAPSHOT"),
                artifacts.stream().map(Artifact::key).toList());
    }

    @Test
    @InjectMojo(goal = "install")
    @MojoParameter(name = "installAtEnd", value = "false")
    public void testBasicInstallWithAttachedArtifacts(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        projectManager.attachArtifact(
                project,
                new ProducedArtifactStub("org.apache.maven.test", "attached-artifact-test", "", "1.0-SNAPSHOT", "jar"),
                Paths.get(getBasedir(), "target/test-classes/unit/attached-artifact-test-1.0-SNAPSHOT.jar"));
        artifactManager.setPath(
                project.getMainArtifact().get(),
                Paths.get(getBasedir(), "target/test-classes/unit/maven-install-test-1.0-SNAPSHOT.jar"));

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Collection<ProducedArtifact> artifacts = request.getArtifacts();
        assertEquals(
                Arrays.asList(
                        "org.apache.maven.test:maven-install-test:pom:1.0-SNAPSHOT",
                        "org.apache.maven.test:maven-install-test:jar:1.0-SNAPSHOT",
                        "org.apache.maven.test:attached-artifact-test:jar:1.0-SNAPSHOT"),
                artifacts.stream().map(Artifact::key).toList());
    }

    @Test
    @InjectMojo(goal = "install")
    public void testInstallIfArtifactFileIsNull(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        assertFalse(artifactManager.getPath(project.getMainArtifact().get()).isPresent());

        MojoException e = assertThrows(MojoException.class, mojo::execute, "Did not throw mojo execution exception");
        assertEquals("The packaging for this project did not assign a file to the build artifact", e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install")
    public void testSkip(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        setVariableValueToObject(mojo, "session", this.session);
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        mojo.setSkip(true);

        assertNull(execute(mojo));
    }

    @Provides
    @Singleton
    @Priority(10)
    @SuppressWarnings("unused")
    private InternalSession createSession() {
        InternalSession session = SessionMock.getMockSession(LOCAL_REPO);
        when(session.getArtifact(any()))
                .thenAnswer(iom -> new org.apache.maven.internal.impl.DefaultArtifact(
                        session, iom.getArgument(0, org.eclipse.aether.artifact.Artifact.class)));
        return session;
    }

    @Provides
    @Singleton
    @SuppressWarnings("unused")
    private Project createProject(InternalSession session) {
        ProjectStub project = new ProjectStub();
        project.setBasedir(Paths.get(getBasedir()));
        project.setPomPath(Paths.get(getBasedir(), "src/test/resources/unit/pom.xml"));
        project.setGroupId("org.apache.maven.test");
        project.setArtifactId("maven-install-test");
        project.setVersion("1.0-SNAPSHOT");
        project.setPackaging("jar");
        ProducedArtifactStub artifact =
                new ProducedArtifactStub("org.apache.maven.test", "maven-install-test", "", "1.0-SNAPSHOT", "jar");
        project.setMainArtifact(artifact);
        return project;
    }

    @Provides
    @Singleton
    private MojoExecution createMojoExecution() {
        return new MojoExecutionStub("default-install", "install");
    }

    @Provides
    @Singleton
    @Named("dummy.reactorProjects")
    private List<Project> getDummyReactorProjects() {
        return Collections.emptyList();
    }

    private <T> ArtifactInstallerRequest execute(Mojo mojo) {
        return execute(mojo, null);
    }

    private ArtifactInstallerRequest execute(Mojo mojo, Consumer<ArtifactInstallerRequest> consumer) {
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
}

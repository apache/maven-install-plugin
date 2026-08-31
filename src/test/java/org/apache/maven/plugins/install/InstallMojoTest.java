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
import java.lang.reflect.Method;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.MojoExecution;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Named;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.PluginExecution;
import org.apache.maven.api.plugin.Log;
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
import org.apache.maven.impl.InternalSession;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MojoTest
class InstallMojoTest {

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
    void setUp() throws Exception {
        FileUtils.deleteDirectory(new File(getBasedir() + "/" + LOCAL_REPO));
    }

    @Test
    @InjectMojo(goal = "install")
    void installTestEnvironment(InstallMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    @InjectMojo(goal = "install")
    @MojoParameter(name = "installAtEnd", value = "false")
    void basicInstall(InstallMojo mojo) throws Exception {
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
    void basicInstallWithAttachedArtifacts(InstallMojo mojo) throws Exception {
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
    void installIfArtifactFileIsNull(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        assertFalse(artifactManager.getPath(project.getMainArtifact().get()).isPresent());

        MojoException e = assertThrows(MojoException.class, mojo::execute, "Did not throw mojo execution exception");
        assertEquals("The packaging for this project did not assign a file to the build artifact", e.getMessage());
    }

    @Test
    @InjectMojo(goal = "install")
    void skip(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        setVariableValueToObject(mojo, "session", this.session);
        mojo.setSkip(true);

        assertNull(execute(mojo));
    }

    @Test
    @InjectMojo(goal = "install")
    void usingPluginMatchesAnyExecutionId(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        // a module binding the goal under a custom execution id must not observe a singleton
        // project set and install mid-build (broken all-or-nothing contract)
        assertTrue(invokeUsingPlugin(mojo, projectUsingInstallPlugin("custom-install-id", "install")));
    }

    @Test
    @InjectMojo(goal = "install")
    void usingPluginIgnoresExecutionsBoundToNone(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        assertFalse(invokeUsingPlugin(mojo, projectUsingInstallPlugin("default-install", "none")));
    }

    @Test
    @InjectMojo(goal = "install")
    void midLoopFailureLogsPartialInstallInventory(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        setVariableValueToObject(mojo, "session", session);
        Log log = mock(Log.class);
        setVariableValueToObject(mojo, "log", log);
        mojo.setSkip(true);

        Project moduleA = reactorProject("module-a");
        Project moduleB = reactorProject("module-b");
        ArtifactInstallerRequest requestA = mock(ArtifactInstallerRequest.class);
        ArtifactInstallerRequest requestB = mock(ArtifactInstallerRequest.class);
        when(session.getProjects()).thenReturn(Arrays.asList(moduleA, moduleB));
        when(session.getPluginContext(moduleA)).thenReturn(deferredContext(requestA));
        when(session.getPluginContext(moduleB)).thenReturn(deferredContext(requestB));
        doThrow(new MojoException("install failed")).when(artifactInstaller).install(requestB);

        assertThrows(MojoException.class, mojo::execute);

        verify(log, atLeastOnce()).error(contains("partially installed state"));
        verify(log).error(contains("installed: org.apache.maven.test:module-a:1.0-SNAPSHOT"));
        verify(log).error(contains("failed: org.apache.maven.test:module-b:1.0-SNAPSHOT"));
    }

    @Test
    @InjectMojo(goal = "install")
    void deferredInstallRunsExactlyOncePerProject(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        setVariableValueToObject(mojo, "session", session);
        setVariableValueToObject(mojo, "log", mock(Log.class));
        mojo.setSkip(true);

        Project moduleA = reactorProject("module-a");
        Project moduleB = reactorProject("module-b");
        when(session.getProjects()).thenReturn(Arrays.asList(moduleA, moduleB));
        when(session.getPluginContext(moduleA)).thenReturn(deferredContext(mock(ArtifactInstallerRequest.class)));
        when(session.getPluginContext(moduleB)).thenReturn(deferredContext(mock(ArtifactInstallerRequest.class)));

        // first trigger runs the deferred loop for both projects...
        mojo.execute();
        // ...a repeated trigger in the same session (e.g. `mvn install install`) must not re-install
        mojo.execute();

        verify(artifactInstaller, times(2)).install(any(ArtifactInstallerRequest.class));
    }

    private Project reactorProject(String artifactId) {
        Project project = mock(Project.class);
        when(project.getBuild()).thenReturn(installPluginBuild("default-install", "install"));
        when(project.getGroupId()).thenReturn("org.apache.maven.test");
        when(project.getArtifactId()).thenReturn(artifactId);
        when(project.getVersion()).thenReturn("1.0-SNAPSHOT");
        return project;
    }

    private static Map<String, Object> deferredContext(ArtifactInstallerRequest request) {
        Map<String, Object> pluginContext = new HashMap<>();
        pluginContext.put(InstallMojo.class.getName() + ".processed", "TO_BE_INSTALLED");
        pluginContext.put(ArtifactInstallerRequest.class.getName(), request);
        return pluginContext;
    }

    private static Project projectUsingInstallPlugin(String executionId, String phase) {
        Project project = mock(Project.class);
        when(project.getBuild()).thenReturn(installPluginBuild(executionId, phase));
        return project;
    }

    private static Build installPluginBuild(String executionId, String phase) {
        Plugin plugin = Plugin.newBuilder()
                .groupId("org.apache.maven.plugins")
                .artifactId("maven-install-plugin")
                .executions(Collections.singletonList(PluginExecution.newBuilder()
                        .id(executionId)
                        .phase(phase)
                        .build()))
                .build();
        return Build.newBuilder().plugins(Collections.singletonList(plugin)).build();
    }

    private static boolean invokeUsingPlugin(InstallMojo mojo, Project project) throws Exception {
        Method usingPlugin = InstallMojo.class.getDeclaredMethod("usingPlugin", Project.class);
        usingPlugin.setAccessible(true);
        return (Boolean) usingPlugin.invoke(mojo, project);
    }

    @Provides
    @Singleton
    @Priority(10)
    @SuppressWarnings("unused")
    private InternalSession createSession() {
        InternalSession session = SessionMock.getMockSession(LOCAL_REPO);
        when(session.getArtifact(any()))
                .thenAnswer(iom -> new org.apache.maven.impl.DefaultArtifact(
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

    private ArtifactInstallerRequest execute(Mojo mojo) throws Exception {
        AtomicReference<ArtifactInstallerRequest> request = new AtomicReference<>();
        doAnswer(iom -> {
                    ArtifactInstallerRequest req = iom.getArgument(0, ArtifactInstallerRequest.class);
                    request.set(req);
                    return null;
                })
                .when(artifactInstaller)
                .install(any(ArtifactInstallerRequest.class));
        mojo.execute();
        return request.get();
    }
}

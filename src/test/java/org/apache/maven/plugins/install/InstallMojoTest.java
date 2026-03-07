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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import org.apache.maven.api.di.Provides;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.installation.InstallRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@ExtendWith(MockitoExtension.class)
@MojoTest
public class InstallMojoTest {

    @TempDir
    private Path tempDir;

    @Inject
    private MavenProject mavenProject;

    @Inject
    private MavenSession mavenSession;

    @Inject
    private MavenProjectHelper mavenProjectHelper;

    @Mock
    private RepositorySystem repositorySystem;

    @Captor
    private ArgumentCaptor<InstallRequest> installRequestCaptor;

    @Provides
    @SuppressWarnings("unused")
    private RepositorySystem repositorySystemProvides() {
        return repositorySystem;
    }

    @BeforeEach
    public void setUp() throws Exception {
        mavenProject.setGroupId("org.apache.maven.test");
        mavenProject.setArtifactId("maven-install-test");
        mavenProject.setVersion("1.0-SNAPSHOT");
        mavenProject.setPackaging("jar");

        Plugin plugin = new Plugin();
        plugin.setArtifactId("maven-install-plugin");
        PluginExecution execution = new PluginExecution();
        execution.setGoals(Collections.singletonList("install"));
        plugin.setExecutions(Collections.singletonList(execution));
        mavenProject.getBuild().addPlugin(plugin);

        lenient().when(mavenSession.getProjects()).thenReturn(Collections.singletonList(mavenProject));
    }

    @Test
    @InjectMojo(goal = "install")
    public void testBasicInstall(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        setProjectArtifact(mavenProject);

        mojo.execute();

        verify(repositorySystem).install(any(), installRequestCaptor.capture());

        InstallRequest installRequest = installRequestCaptor.getValue();
        ArrayList<org.eclipse.aether.artifact.Artifact> artifacts = new ArrayList<>(installRequest.getArtifacts());
        assertEquals(2, artifacts.size());

        assertArtifactInstalled(mavenProject, artifacts.get(0), "pom");
        assertArtifactInstalled(mavenProject, artifacts.get(1), "jar");
    }

    @Test
    @InjectMojo(goal = "install")
    public void testBasicInstallWithAttachedArtifacts(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        setProjectArtifact(mavenProject);

        mavenProjectHelper.attachArtifact(
                mavenProject,
                "jar",
                "next1",
                Files.createTempFile(tempDir, "test-artifact1", "jar").toFile());
        mavenProjectHelper.attachArtifact(
                mavenProject,
                "jar",
                "next2",
                Files.createTempFile(tempDir, "test-artifact2", "jar").toFile());

        mojo.execute();

        verify(repositorySystem).install(any(), installRequestCaptor.capture());

        InstallRequest installRequest = installRequestCaptor.getValue();
        ArrayList<org.eclipse.aether.artifact.Artifact> artifacts = new ArrayList<>(installRequest.getArtifacts());

        assertEquals(4, artifacts.size());
        assertArtifactInstalled(mavenProject, artifacts.get(0), "pom");
        assertArtifactInstalled(mavenProject, artifacts.get(1), "jar");
        assertArtifactInstalled(mavenProject, artifacts.get(2), "next1", "jar");
        assertArtifactInstalled(mavenProject, artifacts.get(3), "next2", "jar");
    }

    @Test
    @InjectMojo(goal = "install")
    public void testNonPomInstallWithAttachedArtifactsOnly(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        setProjectArtifact(mavenProject);

        mavenProjectHelper.attachArtifact(
                mavenProject,
                "jar",
                "next1",
                Files.createTempFile(tempDir, "test-artifact1", "jar").toFile());

        mavenProject.getArtifact().setFile(null);

        try {
            mojo.execute();
            fail("Did not throw mojo execution exception");
        } catch (MojoExecutionException e) {
            // expected, message should include artifactId
            assertEquals(
                    "The packaging plugin for project maven-install-test did not assign a main file to the project "
                            + "but it has attachments. Change packaging to 'pom'.",
                    e.getMessage());
        }
        verifyNoInteractions(repositorySystem);
    }

    @Test
    @InjectMojo(goal = "install")
    public void testInstallIfArtifactFileIsNull(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        setProjectArtifact(mavenProject);
        mavenProject.getArtifact().setFile(null);

        try {
            mojo.execute();
            fail("Did not throw mojo execution exception");
        } catch (MojoExecutionException e) {
            // expected, message should include artifactId
            assertEquals(
                    "The packaging plugin for project maven-install-test did not assign a file to the build artifact",
                    e.getMessage());
        }
        verifyNoInteractions(repositorySystem);
    }

    @Test
    @InjectMojo(goal = "install")
    public void testInstallIfProjectFileIsNull(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        setProjectArtifact(mavenProject);
        mavenProject.setFile(null);

        try {
            mojo.execute();
            fail("Did not throw mojo execution exception");
        } catch (MojoExecutionException e) {
            // expected, message should include artifactId
            assertEquals("The POM for project maven-install-test could not be attached", e.getMessage());
        }
        verifyNoInteractions(repositorySystem);
    }

    @Test
    @InjectMojo(goal = "install")
    public void testInstallIfPackagingIsPom(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        mavenProject.setPomFile(
                Files.createTempFile(tempDir, "test-artifact", "pom").toFile());
        mavenProject.setPackaging("pom");

        mojo.execute();

        verify(repositorySystem).install(any(), installRequestCaptor.capture());

        InstallRequest installRequest = installRequestCaptor.getValue();
        ArrayList<org.eclipse.aether.artifact.Artifact> artifacts = new ArrayList<>(installRequest.getArtifacts());

        assertEquals(1, artifacts.size());
        assertArtifactInstalled(mavenProject, artifacts.get(0), "pom");
    }

    @Test
    @InjectMojo(goal = "install")
    public void testInstallIfPackagingIsBom(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        mavenProject.setPomFile(
                Files.createTempFile(tempDir, "test-artifact", "pom").toFile());
        mavenProject.setPackaging("bom");

        mojo.execute();

        verify(repositorySystem).install(any(), installRequestCaptor.capture());

        InstallRequest installRequest = installRequestCaptor.getValue();
        ArrayList<org.eclipse.aether.artifact.Artifact> artifacts = new ArrayList<>(installRequest.getArtifacts());

        assertEquals(1, artifacts.size());
        assertArtifactInstalled(mavenProject, artifacts.get(0), "pom");
    }

    @Test
    @InjectMojo(goal = "install")
    @MojoParameter(name = "skip", value = "true")
    public void testSkip(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());

        mojo.execute();

        verifyNoInteractions(repositorySystem);
    }

    private void assertArtifactInstalled(
            MavenProject mavenProject, org.eclipse.aether.artifact.Artifact artifact, String type) {
        assertEquals(mavenProject.getArtifactId(), artifact.getArtifactId());
        assertEquals(mavenProject.getGroupId(), artifact.getGroupId());
        assertEquals(mavenProject.getVersion(), artifact.getVersion());
        assertEquals(type, artifact.getExtension());
    }

    private void assertArtifactInstalled(MavenProject mavenProject, Artifact artifact, String classifier, String type) {
        assertArtifactInstalled(mavenProject, artifact, type);
        assertEquals(classifier, artifact.getClassifier());
    }

    private void setProjectArtifact(MavenProject mavenProject) throws IOException {
        org.apache.maven.artifact.DefaultArtifact artifact = new org.apache.maven.artifact.DefaultArtifact(
                mavenProject.getGroupId(),
                mavenProject.getArtifactId(),
                mavenProject.getVersion(),
                null,
                "jar",
                null,
                new DefaultArtifactHandler("jar"));
        artifact.setFile(Files.createTempFile(tempDir, "test-artifact", "jar").toFile());
        mavenProject.setArtifact(artifact);
        mavenProject.setPomFile(
                Files.createTempFile(tempDir, "test-artifact", "pom").toFile());
    }
}

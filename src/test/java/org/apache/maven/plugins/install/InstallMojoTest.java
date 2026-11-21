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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.lenient;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest(realRepositorySession = true)
class InstallMojoTest {

    @TempDir
    private Path tempDir;

    @Inject
    private MavenProject mavenProject;

    @Inject
    private MavenSession mavenSession;

    @Inject
    private MavenProjectHelper mavenProjectHelper;

    private File localRepo;

    @BeforeEach
    void setUp() throws Exception {
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

        localRepo = tempDir.resolve("local-repo").toAbsolutePath().toFile();
        mavenSession.getRequest().setLocalRepositoryPath(localRepo);
    }

    @Test
    @InjectMojo(goal = "install")
    void testBasicInstall(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        setProjectArtifact(mavenProject);

        mojo.execute();

        Artifact artifact = mavenProject.getArtifact();
        String groupId = dotToSlashReplacer(artifact.getGroupId());

        File installedArtifact = new File(
                localRepo,
                groupId + "/" + artifact.getArtifactId() + "/" + artifact.getVersion() + "/"
                        + artifact.getArtifactId() + "-" + artifact.getVersion() + "."
                        + artifact.getArtifactHandler().getExtension());

        assertTrue(installedArtifact.exists());

        assertEquals(5, FileUtils.getFiles(localRepo, null, null).size());
    }

    @Test
    @InjectMojo(goal = "install")
    void testBasicInstallWithAttachedArtifacts(InstallMojo mojo) throws Exception {
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

        List<Artifact> attachedArtifacts = mavenProject.getAttachedArtifacts();

        for (Artifact attachedArtifact : attachedArtifacts) {

            String groupId = dotToSlashReplacer(attachedArtifact.getGroupId());

            File installedArtifact = new File(
                    localRepo,
                    groupId + "/" + attachedArtifact.getArtifactId()
                            + "/" + attachedArtifact.getVersion() + "/" + attachedArtifact.getArtifactId()
                            + "-" + attachedArtifact.getVersion() + "-" + attachedArtifact.getClassifier()
                            + "." + attachedArtifact.getArtifactHandler().getExtension());

            assertTrue(installedArtifact.exists(), installedArtifact.getPath() + " does not exist");
        }

        assertEquals(7, FileUtils.getFiles(localRepo, null, null).size());
    }

    @Test
    @InjectMojo(goal = "install")
    void testNonPomInstallWithAttachedArtifactsOnly(InstallMojo mojo) throws Exception {
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
    }

    @Test
    @InjectMojo(goal = "install")
    void testInstallIfArtifactFileIsNull(InstallMojo mojo) throws Exception {
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
    }

    @Test
    @InjectMojo(goal = "install")
    void testInstallIfProjectFileIsNull(InstallMojo mojo) throws Exception {
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
    }

    @Test
    @InjectMojo(goal = "install")
    void testInstallIfPackagingIsPom(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        mavenProject.setPomFile(
                Files.createTempFile(tempDir, "test-artifact", "pom").toFile());
        mavenProject.setPackaging("pom");

        mojo.execute();

        String groupId = dotToSlashReplacer(mavenProject.getGroupId());

        File installedArtifact = new File(
                localRepo,
                groupId + "/" + mavenProject.getArtifactId() + "/" + mavenProject.getVersion() + "/"
                        + mavenProject.getArtifactId() + "-" + mavenProject.getVersion() + "."
                        + mavenProject.getPackaging());

        assertTrue(installedArtifact.exists());

        assertEquals(4, FileUtils.getFiles(localRepo, null, null).size());
    }

    @Test
    @InjectMojo(goal = "install")
    void testInstallIfPackagingIsBom(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        mavenProject.setPomFile(
                Files.createTempFile(tempDir, "test-artifact", "pom").toFile());
        mavenProject.setPackaging("bom");

        mojo.execute();

        String groupId = dotToSlashReplacer(mavenProject.getGroupId());

        File installedArtifact = new File(
                localRepo,
                groupId + "/" + mavenProject.getArtifactId() + "/" + mavenProject.getVersion() + "/"
                        + mavenProject.getArtifactId() + "-" + mavenProject.getVersion() + ".pom");

        assertTrue(installedArtifact.exists());

        assertEquals(4, FileUtils.getFiles(localRepo, null, null).size());
    }

    @Test
    @InjectMojo(goal = "install")
    @MojoParameter(name = "skip", value = "true")
    void skip(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        mavenProject.setGroupId("org.apache.maven.its.install.skip");
        mavenProject.setArtifactId("test");
        mavenProject.setVersion("1.0");
        setProjectArtifact(mavenProject);

        mojo.execute();

        Artifact artifact = mavenProject.getArtifact();
        String groupId = dotToSlashReplacer(artifact.getGroupId());

        File installedArtifact = new File(
                localRepo,
                groupId + "/" + artifact.getArtifactId() + "/" + artifact.getVersion() + "/"
                        + artifact.getArtifactId() + "-" + artifact.getVersion() + "."
                        + artifact.getArtifactHandler().getExtension());

        assertFalse(installedArtifact.exists());

        // Assertions from the skip-install IT
        assertNotInstalled(localRepo, "org/apache/maven/its/install/skip/test/maven-metadata-local.xml");
        assertNotInstalled(localRepo, "org/apache/maven/its/install/skip/test/1.0/test-1.0.pom");
        assertNotInstalled(localRepo, "org/apache/maven/its/install/skip/test/1.0/test-1.0.jar");

        assertFalse(localRepo.exists());
    }

    // -------------------------------------------------------------------------
    // Tests converted from src/it (see the IT-to-unit mapping in the report)
    // -------------------------------------------------------------------------

    @Test
    @InjectMojo(goal = "install")
    void noMainArtifactWithAttachmentsFailsWhenOutputDirMissing(InstallMojo mojo) throws Exception {
        assertNoMainArtifactWithAttachments(mojo, false);
    }

    @Test
    @InjectMojo(goal = "install")
    void noMainArtifactWithAttachmentsFailsWhenOutputDirPresent(InstallMojo mojo) throws Exception {
        assertNoMainArtifactWithAttachments(mojo, true);
    }

    @Test
    @InjectMojo(goal = "install")
    void attachedJarInstalledWithoutChecksums(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        mavenProject.setGroupId("org.apache.maven.its.install.ajc");
        mavenProject.setArtifactId("test");
        mavenProject.setVersion("1.0-SNAPSHOT");
        setProjectArtifact(mavenProject);
        mavenProjectHelper.attachArtifact(
                mavenProject,
                "jar",
                "sources",
                Files.createTempFile(tempDir, "test-1.0-SNAPSHOT-sources", "jar")
                        .toFile());

        mojo.execute();

        String versionDir = "org/apache/maven/its/install/ajc/test/1.0-SNAPSHOT/";
        String[] installed = {
            versionDir + "test-1.0-SNAPSHOT.pom",
            versionDir + "test-1.0-SNAPSHOT.jar",
            versionDir + "test-1.0-SNAPSHOT-sources.jar",
            versionDir + "maven-metadata-local.xml",
            "org/apache/maven/its/install/ajc/test/maven-metadata-local.xml"
        };
        for (String path : installed) {
            assertInstalled(localRepo, path);
            InstallTestHelper.assertNoChecksum(localRepo, path);
        }
    }

    @Test
    @InjectMojo(goal = "install")
    void pomPackagingInstalledWithoutChecksums(InstallMojo mojo) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        mavenProject.setGroupId("org.apache.maven.its.install.pc");
        mavenProject.setArtifactId("test");
        mavenProject.setVersion("1.0-SNAPSHOT");
        mavenProject.setPackaging("pom");
        mavenProject.setPomFile(Files.createTempFile(tempDir, "test", "pom").toFile());

        mojo.execute();

        String versionDir = "org/apache/maven/its/install/pc/test/1.0-SNAPSHOT/";
        String[] installed = {
            versionDir + "test-1.0-SNAPSHOT.pom",
            versionDir + "maven-metadata-local.xml",
            "org/apache/maven/its/install/pc/test/maven-metadata-local.xml"
        };
        for (String path : installed) {
            assertInstalled(localRepo, path);
        }
        InstallTestHelper.assertNoChecksum(localRepo, versionDir + "test-1.0-SNAPSHOT.pom");
    }

    private void assertNoMainArtifactWithAttachments(InstallMojo mojo, boolean classesDirPresent) throws Exception {
        mojo.setPluginContext(new HashMap<>());
        mavenProject.setGroupId(
                classesDirPresent ? "org.apache.maven.its.install.nma2" : "org.apache.maven.its.install.nma1");
        mavenProject.setArtifactId("test");
        mavenProject.setVersion("1.0-SNAPSHOT");
        setProjectArtifact(mavenProject);
        mavenProjectHelper.attachArtifact(
                mavenProject,
                "jar",
                "it",
                Files.createTempFile(tempDir, "test", "jar").toFile());

        // The two IT variants differ only by the presence of target/classes. Mirror that under the temp
        // directory so the real build output is untouched.
        Path classesDir = tempDir.resolve("target/classes");
        if (classesDirPresent) {
            Files.createDirectories(classesDir);
            assertTrue(Files.isDirectory(classesDir));
        } else {
            assertFalse(Files.exists(classesDir));
        }

        mavenProject.getArtifact().setFile(null);

        MojoExecutionException e = assertThrows(MojoExecutionException.class, mojo::execute);
        assertEquals(
                "The packaging plugin for project test did not assign a main file to the project but it has "
                        + "attachments. Change packaging to 'pom'.",
                e.getMessage());
    }

    private void assertInstalled(File repo, String path) {
        File installed = new File(repo, path);
        assertTrue(installed.isFile(), "Missing installed file: " + installed);
    }

    private void assertNotInstalled(File repo, String path) {
        File installed = new File(repo, path);
        assertFalse(installed.isFile(), "File should not be installed: " + installed);
    }

    private String dotToSlashReplacer(String parameter) {
        return parameter.replace('.', '/');
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

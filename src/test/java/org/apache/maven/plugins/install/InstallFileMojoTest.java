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

import org.apache.maven.api.plugin.testing.Basedir;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.getBasedir;
import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest(realRepositorySession = true)
public class InstallFileMojoTest {

    private static final String LOCAL_REPO = "target/local-repo/";
    private static final String SPECIFIC_LOCAL_REPO = "target/specific-local-repo/";

    @Inject
    private MavenSession mavenSession;

    @BeforeEach
    public void setUp() throws Exception {
        FileUtils.deleteDirectory(new File(getBasedir() + "/" + LOCAL_REPO));
        FileUtils.deleteDirectory(new File(getBasedir() + "/" + SPECIFIC_LOCAL_REPO));

        mavenSession.getRequest().setLocalRepositoryPath(getBasedir() + "/" + LOCAL_REPO);
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    @MojoParameter(name = "localRepositoryPath", value = "${basedir}/" + SPECIFIC_LOCAL_REPO)
    @Basedir("/unit/install-file-basic-test")
    public void testInstallFileWithLocalRepositoryPath(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        File installedArtifact = new File(
                getBasedir(),
                SPECIFIC_LOCAL_REPO
                        + "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.jar");

        assertTrue(
                installedArtifact.exists(), "Installed artifact should exist: " + installedArtifact.getAbsolutePath());

        assertEquals(
                5,
                FileUtils.getFiles(new File(getBasedir(), SPECIFIC_LOCAL_REPO), null, null)
                        .size(),
                FileUtils.getFiles(new File(getBasedir(), SPECIFIC_LOCAL_REPO), null, null)
                        .toString());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    @Basedir("/unit/install-file-basic-test")
    public void testBasicInstallFile(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        File installedArtifact = new File(
                getBasedir(),
                LOCAL_REPO
                        + "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.jar");

        assertTrue(
                installedArtifact.exists(), "Installed artifact should exist: " + installedArtifact.getAbsolutePath());

        assertEquals(
                5,
                FileUtils.getFiles(new File(getBasedir(), LOCAL_REPO), null, null)
                        .size());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "classifier", value = "sources")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    @Basedir("/unit/install-file-basic-test")
    public void testInstallFileWithClassifier(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        File installedArtifact = new File(
                getBasedir(),
                LOCAL_REPO
                        + "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT-sources.jar");

        assertTrue(
                installedArtifact.exists(), "Installed artifact should exist: " + installedArtifact.getAbsolutePath());

        assertEquals(
                5,
                FileUtils.getFiles(new File(getBasedir(), LOCAL_REPO), null, null)
                        .size());
    }

    @Test
    @InjectMojo(goal = "install-file")
    @MojoParameter(name = "groupId", value = "org.apache.maven.test")
    @MojoParameter(name = "artifactId", value = "maven-install-file-test")
    @MojoParameter(name = "version", value = "1.0-SNAPSHOT")
    @MojoParameter(name = "packaging", value = "jar")
    @MojoParameter(name = "generatePom", value = "true")
    @MojoParameter(name = "file", value = "${basedir}/target/maven-install-test-1.0-SNAPSHOT.jar")
    @Basedir("/unit/install-file-test-generatePom")
    public void testInstallFileWithGeneratePom(InstallFileMojo mojo) throws Exception {
        mojo.execute();

        File installedArtifact = new File(
                getBasedir(),
                LOCAL_REPO
                        + "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.jar");

        assertTrue(
                installedArtifact.exists(), "Installed artifact should exist: " + installedArtifact.getAbsolutePath());

        File installedPom = new File(
                getBasedir(),
                LOCAL_REPO
                        + "org/apache/maven/test/maven-install-file-test/1.0-SNAPSHOT/maven-install-file-test-1.0-SNAPSHOT.pom");

        try (Reader reader = new XmlStreamReader(installedPom)) {
            Model model = new MavenXpp3Reader().read(reader);

            assertEquals("4.0.0", model.getModelVersion());
            assertEquals("org.apache.maven.test", model.getGroupId());
            assertEquals("maven-install-file-test", model.getArtifactId());
            assertEquals("1.0-SNAPSHOT", model.getVersion());
        }

        assertEquals(
                5,
                FileUtils.getFiles(new File(getBasedir(), LOCAL_REPO), null, null)
                        .size());
    }

    public void testInstallFileWithPomFile(InstallFileMojo mojo) throws Exception {
        //        File testPom =
        //                new File(getBasedir(),
        // "target/test-classes/unit/install-file-with-pomFile-test/plugin-config.xml");

        //        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        assertNotNull(mojo);

        //        setVariableValueToObject(mojo, "session", createMavenSession(LOCAL_REPO));

        mojo.execute();

        File pomFile = (File) getVariableValueFromObject(mojo, "pomFile");

        assertTrue(pomFile.exists());

        //        File installedArtifact = new File(
        //                getBasedir(),
        //                LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version +
        // "."
        //                        + packaging);
        //
        //        assertTrue(installedArtifact.exists());
        //
        //        File installedPom = new File(
        //                getBasedir(),
        //                LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version +
        // "."
        //                        + "pom");
        //
        //        assertTrue(installedPom.exists());

        assertEquals(5, FileUtils.getFiles(new File(LOCAL_REPO), null, null).size());
    }

    public void testInstallFileWithPomAsPackaging(InstallFileMojo mojo) throws Exception {
        //        File testPom = new File(
        //                getBasedir(), "target/test-classes/unit/install-file-with-pom-as-packaging/" +
        // "plugin-config.xml");

        //        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        assertNotNull(mojo);

        //        setVariableValueToObject(mojo, "session", createMavenSession(LOCAL_REPO));

        //        assertTrue(file.exists());

        //        assertEquals("pom", packaging);

        mojo.execute();

        //        File installedPom = new File(
        //                getBasedir(),
        //                LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version +
        // "."
        //                        + "pom");

        //        assertTrue(installedPom.exists());

        assertEquals(4, FileUtils.getFiles(new File(LOCAL_REPO), null, null).size());
    }
}

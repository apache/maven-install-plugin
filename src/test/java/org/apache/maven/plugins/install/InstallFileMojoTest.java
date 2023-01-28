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
import java.io.Reader;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.EnhancedLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.NoLocalRepositoryManagerException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
public class InstallFileMojoTest extends AbstractMojoTestCase {
    private String groupId;

    private String artifactId;

    private String version;

    private String packaging;

    private String classifier;

    private File file;

    private final String LOCAL_REPO = "target/local-repo/";

    private final String SPECIFIC_LOCAL_REPO = "target/specific-local-repo/";

    public void setUp() throws Exception {
        super.setUp();

        FileUtils.deleteDirectory(new File(getBasedir() + "/" + LOCAL_REPO));
        FileUtils.deleteDirectory(new File(getBasedir() + "/" + SPECIFIC_LOCAL_REPO));
    }

    public void testInstallFileFromLocalRepositoryToLocalRepositoryPath() throws Exception {
        File localRepository =
                new File(getBasedir(), "target/test-classes/unit/install-file-from-local-repository-test/target");

        File testPom = new File(localRepository.getParentFile(), "plugin-config.xml");

        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        assertNotNull(mojo);

        setVariableValueToObject(mojo, "session", createMavenSession(localRepository.getAbsolutePath()));

        File specificLocalRepositoryPath = new File(getBasedir() + "/" + SPECIFIC_LOCAL_REPO);

        setVariableValueToObject(mojo, "localRepositoryPath", specificLocalRepositoryPath);

        assignValuesForParameter(mojo);

        mojo.execute();

        String localPath = getBasedir() + "/" + SPECIFIC_LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/"
                + artifactId + "-" + version;

        File installedArtifact = new File(localPath + "." + "jar");

        assertTrue(installedArtifact.exists());

        assertEquals(
                FileUtils.getFiles(new File(SPECIFIC_LOCAL_REPO), null, null).toString(),
                5,
                FileUtils.getFiles(new File(SPECIFIC_LOCAL_REPO), null, null).size());
    }

    public void testInstallFileWithLocalRepositoryPath() throws Exception {
        File testPom =
                new File(getBasedir(), "target/test-classes/unit/install-file-with-checksum/" + "plugin-config.xml");

        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        assertNotNull(mojo);

        setVariableValueToObject(mojo, "session", createMavenSession(LOCAL_REPO));

        File specificLocalRepositoryPath = new File(getBasedir() + "/" + SPECIFIC_LOCAL_REPO);

        setVariableValueToObject(mojo, "localRepositoryPath", specificLocalRepositoryPath);

        assignValuesForParameter(mojo);

        mojo.execute();

        String localPath = getBasedir() + "/" + SPECIFIC_LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/"
                + artifactId + "-" + version;

        File installedArtifact = new File(localPath + "." + "jar");

        assertTrue(installedArtifact.exists());

        assertEquals(
                FileUtils.getFiles(new File(SPECIFIC_LOCAL_REPO), null, null).toString(),
                5,
                FileUtils.getFiles(new File(SPECIFIC_LOCAL_REPO), null, null).size());
    }

    public void testInstallFileTestEnvironment() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/install-file-basic-test/plugin-config.xml");

        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        setVariableValueToObject(mojo, "session", createMavenSession(LOCAL_REPO));

        assertNotNull(mojo);
    }

    public void testBasicInstallFile() throws Exception {
        File testPom = new File(getBasedir(), "target/test-classes/unit/install-file-basic-test/plugin-config.xml");

        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        assertNotNull(mojo);

        setVariableValueToObject(mojo, "session", createMavenSession(LOCAL_REPO));

        assignValuesForParameter(mojo);

        mojo.execute();

        File installedArtifact = new File(
                getBasedir(),
                LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "."
                        + packaging);

        assertTrue(installedArtifact.exists());

        assertEquals(5, FileUtils.getFiles(new File(LOCAL_REPO), null, null).size());
    }

    public void testInstallFileWithClassifier() throws Exception {
        File testPom =
                new File(getBasedir(), "target/test-classes/unit/install-file-with-classifier/plugin-config.xml");

        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        assertNotNull(mojo);

        setVariableValueToObject(mojo, "session", createMavenSession(LOCAL_REPO));

        assignValuesForParameter(mojo);

        assertNotNull(classifier);

        mojo.execute();

        File installedArtifact = new File(
                getBasedir(),
                LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "-"
                        + classifier + "." + packaging);

        assertTrue(installedArtifact.exists());

        assertEquals(5, FileUtils.getFiles(new File(LOCAL_REPO), null, null).size());
    }

    public void testInstallFileWithGeneratePom() throws Exception {
        File testPom =
                new File(getBasedir(), "target/test-classes/unit/install-file-test-generatePom/plugin-config.xml");

        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        assertNotNull(mojo);

        setVariableValueToObject(mojo, "session", createMavenSession(LOCAL_REPO));

        assignValuesForParameter(mojo);

        mojo.execute();

        File installedArtifact = new File(
                getBasedir(),
                LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "."
                        + packaging);

        assertTrue((Boolean) getVariableValueFromObject(mojo, "generatePom"));

        assertTrue(installedArtifact.exists());

        File installedPom = new File(
                getBasedir(),
                LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "."
                        + "pom");

        try (Reader reader = new XmlStreamReader(installedPom)) {
            Model model = new MavenXpp3Reader().read(reader);

            assertEquals("4.0.0", model.getModelVersion());

            assertEquals((String) getVariableValueFromObject(mojo, "groupId"), model.getGroupId());

            assertEquals(artifactId, model.getArtifactId());

            assertEquals(version, model.getVersion());
        }

        assertEquals(5, FileUtils.getFiles(new File(LOCAL_REPO), null, null).size());
    }

    public void testInstallFileWithPomFile() throws Exception {
        File testPom =
                new File(getBasedir(), "target/test-classes/unit/install-file-with-pomFile-test/plugin-config.xml");

        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        assertNotNull(mojo);

        setVariableValueToObject(mojo, "session", createMavenSession(LOCAL_REPO));

        assignValuesForParameter(mojo);

        mojo.execute();

        File pomFile = (File) getVariableValueFromObject(mojo, "pomFile");

        assertTrue(pomFile.exists());

        File installedArtifact = new File(
                getBasedir(),
                LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "."
                        + packaging);

        assertTrue(installedArtifact.exists());

        File installedPom = new File(
                getBasedir(),
                LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "."
                        + "pom");

        assertTrue(installedPom.exists());

        assertEquals(5, FileUtils.getFiles(new File(LOCAL_REPO), null, null).size());
    }

    public void testInstallFileWithPomAsPackaging() throws Exception {
        File testPom = new File(
                getBasedir(), "target/test-classes/unit/install-file-with-pom-as-packaging/" + "plugin-config.xml");

        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        assertNotNull(mojo);

        setVariableValueToObject(mojo, "session", createMavenSession(LOCAL_REPO));

        assignValuesForParameter(mojo);

        assertTrue(file.exists());

        assertEquals("pom", packaging);

        mojo.execute();

        File installedPom = new File(
                getBasedir(),
                LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + "."
                        + "pom");

        assertTrue(installedPom.exists());

        assertEquals(4, FileUtils.getFiles(new File(LOCAL_REPO), null, null).size());
    }

    public void testInstallFile() throws Exception {
        File testPom =
                new File(getBasedir(), "target/test-classes/unit/install-file-with-checksum/" + "plugin-config.xml");

        InstallFileMojo mojo = (InstallFileMojo) lookupMojo("install-file", testPom);

        assertNotNull(mojo);

        setVariableValueToObject(mojo, "session", createMavenSession(LOCAL_REPO));

        assignValuesForParameter(mojo);

        mojo.execute();

        String localPath = getBasedir() + "/" + LOCAL_REPO + groupId + "/" + artifactId + "/" + version + "/"
                + artifactId + "-" + version;

        File installedArtifact = new File(localPath + "." + "jar");

        assertTrue(installedArtifact.exists());

        assertEquals(
                FileUtils.getFiles(new File(LOCAL_REPO), null, null).toString(),
                5,
                FileUtils.getFiles(new File(LOCAL_REPO), null, null).size());
    }

    private void assignValuesForParameter(Object obj) throws Exception {
        this.groupId = dotToSlashReplacer((String) getVariableValueFromObject(obj, "groupId"));

        this.artifactId = (String) getVariableValueFromObject(obj, "artifactId");

        this.version = (String) getVariableValueFromObject(obj, "version");

        this.packaging = (String) getVariableValueFromObject(obj, "packaging");

        this.classifier = (String) getVariableValueFromObject(obj, "classifier");

        this.file = (File) getVariableValueFromObject(obj, "file");
    }

    private String dotToSlashReplacer(String parameter) {
        return parameter.replace('.', '/');
    }

    private MavenSession createMavenSession(String localRepositoryBaseDir) throws NoLocalRepositoryManagerException {
        MavenSession session = mock(MavenSession.class);
        DefaultRepositorySystemSession repositorySession = new DefaultRepositorySystemSession();
        repositorySession.setLocalRepositoryManager(new EnhancedLocalRepositoryManagerFactory()
                .newInstance(repositorySession, new LocalRepository(localRepositoryBaseDir)));
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest();
        buildingRequest.setRepositorySession(repositorySession);
        when(session.getProjectBuildingRequest()).thenReturn(buildingRequest);
        when(session.getRepositorySession()).thenReturn(repositorySession);
        return session;
    }
}

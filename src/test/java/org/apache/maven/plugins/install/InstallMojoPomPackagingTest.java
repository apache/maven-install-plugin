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
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.MojoExecution;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.Type;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.di.Priority;
import org.apache.maven.api.di.Provides;
import org.apache.maven.api.di.Singleton;
import org.apache.maven.api.plugin.Mojo;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoParameter;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.api.plugin.testing.stubs.MojoExecutionStub;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest
public class InstallMojoPomPackagingTest {

    private static final String LOCAL_REPO = "target/local-repo/";

    @Inject
    Session session;

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
    @MojoParameter(name = "installAtEnd", value = "false")
    public void testInstallIfPackagingIsPom(InstallMojo mojo) throws Exception {
        assertNotNull(mojo);
        Project project = (Project) getVariableValueFromObject(mojo, "project");
        String packaging = project.getPackaging().type().id();
        assertEquals(Type.POM, packaging);
        artifactManager.setPath(project.getPomArtifact(), project.getPomPath());

        ArtifactInstallerRequest request = execute(mojo);

        assertNotNull(request);
        Collection<ProducedArtifact> artifacts = request.getArtifacts();
        assertEquals(
                List.of("org.apache.maven.test:maven-install-test:pom:1.0-SNAPSHOT"),
                artifacts.stream().map(Artifact::key).toList());
    }

    @Provides
    @Singleton
    @Priority(10)
    @SuppressWarnings("unused")
    private InternalSession createInternalSession() {
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
        project.setPackaging("pom");
        return project;
    }

    @Provides
    @Singleton
    private MojoExecution createMojoExecution() {
        return new MojoExecutionStub("default-install", "install");
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

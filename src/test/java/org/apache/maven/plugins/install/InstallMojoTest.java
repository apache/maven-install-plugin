package org.apache.maven.plugins.install;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.apache.maven.api.plugin.testing.MojoExtension.setVariableValueToObject;
import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.Mojo;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.services.ArtifactInstaller;
import org.apache.maven.api.services.ArtifactInstallerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugins.install.stubs.ArtifactStub;
import org.apache.maven.plugins.install.stubs.SessionStub;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest
public class InstallMojoTest
{

    private static final String LOCAL_REPO = "target/local-repo/";

    @Inject
    ArtifactInstaller artifactInstaller;

    @Inject
    ArtifactManager artifactManager;

    @Inject
    ProjectManager projectManager;

    @BeforeEach
    public void setUp()
        throws Exception
    {
        FileUtils.deleteDirectory( new File( getBasedir() + "/" + LOCAL_REPO ) );
    }

    @Test
    @InjectMojo( goal = "install", pom = "classpath:/unit/basic-install/test.xml" )
    public void testInstallTestEnvironment( InstallMojo mojo )
    {
        assertNotNull( mojo );
    }

    @Test
    @InjectMojo( goal = "install", pom = "classpath:/unit/basic-install/test.xml" )
    public void testBasicInstall( InstallMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        Project project = (Project) getVariableValueFromObject( mojo, "project" );
        setVariableValueToObject( mojo, "reactorProjects", Collections.singletonList( project ) );
        artifactManager.setPath( project.getArtifact(), Paths.get( getBasedir(), "target/test-classes/unit/basic-install/maven-install-test-1.0-SNAPSHOT.jar" ) );

        ArtifactInstallerRequest request = execute( mojo );

        assertNotNull( request );
        Set<Artifact> artifacts = new HashSet<>( request.getArtifacts() );
        assertEquals( new HashSet<>( Arrays.asList(
                new ArtifactStub( "org.apache.maven.test", "maven-install-test", "", "1.0-SNAPSHOT", "jar"),
                new ArtifactStub( "org.apache.maven.test", "maven-install-test", "", "1.0-SNAPSHOT", "pom")
        ) ), artifacts );
    }

    @Test
    @InjectMojo( goal = "install", pom = "classpath:/unit/basic-install/test.xml" )
    public void testBasicInstallWithAttachedArtifacts( InstallMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        Project project = (Project) getVariableValueFromObject( mojo, "project" );
        setVariableValueToObject( mojo, "reactorProjects", Collections.singletonList( project ) );
        projectManager.attachArtifact( project,
                new ArtifactStub( "org.apache.maven.test", "attached-artifact-test", "", "1.0-SNAPSHOT", "jar" ),
                Paths.get( getBasedir(), "target/test-classes/unit/basic-install/attached-artifact-test-1.0-SNAPASHOT.jar" ) );
        artifactManager.setPath( project.getArtifact(), Paths.get( getBasedir(), "target/test-classes/unit/basic-install/maven-install-test-1.0-SNAPSHOT.jar" ) );

        ArtifactInstallerRequest request = execute( mojo );

        assertNotNull( request );
        Set<Artifact> artifacts = new HashSet<>( request.getArtifacts() );
        assertEquals( new HashSet<>( Arrays.asList(
                new ArtifactStub( "org.apache.maven.test", "maven-install-test", "", "1.0-SNAPSHOT", "jar"),
                new ArtifactStub( "org.apache.maven.test", "maven-install-test", "", "1.0-SNAPSHOT", "pom"),
                new ArtifactStub( "org.apache.maven.test", "attached-artifact-test", "", "1.0-SNAPSHOT", "jar")
        ) ), artifacts );
    }

    @Test
    @InjectMojo( goal = "install", pom = "classpath:/unit/basic-install/test.xml" )
    public void testInstallIfArtifactFileIsNull( InstallMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        Project project = (Project) getVariableValueFromObject( mojo, "project" );
        setVariableValueToObject( mojo, "reactorProjects", Collections.singletonList( project ) );
        assertFalse( artifactManager.getPath( project.getArtifact() ).isPresent() );

        assertThrows( MojoException.class, mojo::execute, "Did not throw mojo execution exception" );
    }

    @Test
    @InjectMojo( goal = "install", pom = "classpath:/unit/basic-install/packaging-pom.xml" )
    public void testInstallIfPackagingIsPom( InstallMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        Project project = (Project) getVariableValueFromObject( mojo, "project" );
        setVariableValueToObject( mojo, "reactorProjects", Collections.singletonList( project ) );
        String packaging = project.getPackaging();
        assertEquals( "pom", packaging );
        artifactManager.setPath( project.getArtifact(), project.getPomPath() );

        ArtifactInstallerRequest request = execute( mojo );

        assertNotNull( request );
        Set<Artifact> artifacts = new HashSet<>( request.getArtifacts() );
        assertEquals( new HashSet<>( Arrays.asList(
                new ArtifactStub( "org.apache.maven.test", "maven-install-test", "", "1.0-SNAPSHOT", "pom")
        ) ), artifacts );
    }

    @Test
    @InjectMojo( goal = "install", pom = "classpath:/unit/basic-install/test.xml" )
    public void testSkip( InstallMojo mojo ) throws Exception
    {
        assertNotNull( mojo );
        Project project = (Project) getVariableValueFromObject( mojo, "project" );
        setVariableValueToObject( mojo, "reactorProjects", Collections.singletonList( project ) );
        mojo.setSkip( true );

        assertNull( execute( mojo ) );
    }


    @Provides @Singleton @SuppressWarnings( "unused" )
    private Session createSession()
    {
        return SessionStub.getMockSession( LOCAL_REPO );
    }

    @Provides @SuppressWarnings( "unused" )
    private ArtifactInstaller createArtifactInstaller( Session session )
    {
        return session.getService( ArtifactInstaller.class );
    }

    @Provides @SuppressWarnings( "unused" )
    private ArtifactManager createArtifactManager( Session session )
    {
        return session.getService( ArtifactManager.class );
    }

    @Provides @SuppressWarnings( "unused" )
    private ProjectManager createProjectManager( Session session )
    {
        return session.getService( ProjectManager.class );
    }

    private <T> ArtifactInstallerRequest execute( Mojo mojo )
    {
        return execute( mojo, null );
    }

    private ArtifactInstallerRequest execute( Mojo mojo, Consumer<ArtifactInstallerRequest> consumer )
    {
        AtomicReference<ArtifactInstallerRequest> request = new AtomicReference<>();
        doAnswer( iom ->
        {
            ArtifactInstallerRequest req = iom.getArgument( 0, ArtifactInstallerRequest.class );
            request.set( req );
            if ( consumer != null )
            {
                consumer.accept( req );
            }
            return null;
        } ).when( artifactInstaller ).install( any( ArtifactInstallerRequest.class) );
        mojo.execute();
        return request.get();
    }

}

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

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.apache.maven.api.Artifact;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.services.ArtifactInstaller;
import org.apache.maven.api.services.ArtifactInstallerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.api.plugin.testing.InjectMojo;
import org.apache.maven.api.plugin.testing.MojoTest;
import org.apache.maven.plugins.install.stubs.ArtifactStub;
import org.apache.maven.plugins.install.stubs.SessionStub;
import org.apache.maven.shared.utils.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.maven.api.plugin.testing.MojoExtension.getVariableValueFromObject;
import static org.codehaus.plexus.testing.PlexusExtension.getBasedir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest
public class InstallFileMojoTest
{
    private static final String LOCAL_REPO = "target/local-repo";

    private String groupId;
    private String artifactId;
    private String version;
    private String packaging;
    private String classifier;
    private File file;

    @Inject
    Session session;

    @Inject
    ArtifactManager artifactManager;

    @Inject
    ArtifactInstaller artifactInstaller;

    @BeforeEach
    public void setUp()
        throws Exception
    {
        FileUtils.deleteDirectory( new File( getBasedir() + "/" + LOCAL_REPO ) );
    }

    @Test
    @InjectMojo( goal = "install-file", pom = "classpath:/unit/install-file/basic-test.xml")
    public void testInstallFileTestEnvironment( InstallFileMojo mojo )
    {
        assertNotNull( mojo );
    }

    @Test
    @InjectMojo( goal = "install-file", pom = "classpath:/unit/install-file/basic-test.xml")
    public void testBasicInstallFile( InstallFileMojo mojo )
            throws Exception
    {
        assertNotNull( mojo );
        assignValuesForParameter( mojo );
        File pomFile = (File) getVariableValueFromObject( mojo, "pomFile" );

        ArtifactInstallerRequest request = execute( mojo );

        assertNotNull( request );
        Set<Artifact> artifacts = new HashSet<>( request.getArtifacts() );
        Artifact pom = getArtifact( null, "pom" );
        Artifact jar = getArtifact( null, "jar" );
        assertEquals( new HashSet<>( Arrays.asList( pom, jar ) ), artifacts );
        assertFileExists( artifactManager.getPath( jar ).orElse( null ) );
        assertFileExists( artifactManager.getPath( jar ).orElse( null ) );
        assertEquals( LOCAL_REPO, request.getSession().getLocalRepository().getPath().toString() );
    }

    @Test
    @InjectMojo( goal = "install-file", pom = "classpath:/unit/install-file/file-absent.xml")
    public void testFileDoesNotExists( InstallFileMojo mojo )
            throws Exception
    {
        assertNotNull( mojo );
        assignValuesForParameter( mojo );

        assertThrows( MojoException.class, mojo::execute );
    }

    @Test
    @InjectMojo( goal = "install-file", pom = "classpath:/unit/install-file/with-classifier.xml")
    public void testInstallFileWithClassifier( InstallFileMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        assignValuesForParameter( mojo );
        assertNotNull( classifier );

        ArtifactInstallerRequest request = execute( mojo );

        assertNotNull( request );
        Set<Artifact> artifacts = new HashSet<>( request.getArtifacts() );
        Artifact pom = getArtifact( null, "pom" );
        Artifact sources = getArtifact( "sources", "jar" );
        assertEquals( new HashSet<>( Arrays.asList( pom, sources ) ), artifacts );
        // pom file does not exists, as it should have been deleted after the installation
        assertFileNotExists( artifactManager.getPath( pom ).get() );
        assertFileExists( artifactManager.getPath( sources ).get() );
        assertEquals( LOCAL_REPO, request.getSession().getLocalRepository().getPath().toString() );
    }

    @Test
    @InjectMojo( goal = "install-file", pom = "classpath:/unit/install-file/test-generatePom.xml")
    public void testInstallFileWithGeneratePom( InstallFileMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        assignValuesForParameter( mojo );
        assertTrue( (Boolean) getVariableValueFromObject( mojo, "generatePom" ) );

        AtomicReference<Model> model = new AtomicReference<>();
        ArtifactInstallerRequest request = execute( mojo, air -> model.set( readModel( getArtifact( null, "pom" ) ) ) );

        assertNotNull( request );
        Set<Artifact> artifacts = new HashSet<>( request.getArtifacts() );
        Artifact pom = getArtifact( null, "pom" );
        Artifact jar = getArtifact( null, "jar" );
        assertEquals( new HashSet<>( Arrays.asList( pom, jar ) ), artifacts );
        assertEquals( "4.0.0", model.get().getModelVersion() );
        assertEquals( getVariableValueFromObject( mojo, "groupId" ), model.get().getGroupId() );
        assertEquals( artifactId, model.get().getArtifactId() );
        assertEquals( version, model.get().getVersion() );
        assertNotNull( artifactManager.getPath( jar ).orElse( null ) );
        assertEquals( LOCAL_REPO, request.getSession().getLocalRepository().getPath().toString() );
    }

    private Model readModel( Artifact pom )
    {
        try
        {
            Path pomPath = artifactManager.getPath( pom ).orElse( null );
            assertNotNull( pomPath );
            try ( InputStream is = Files.newInputStream( pomPath ) )
            {
                return new MavenXpp3Reader().read( is );
            }
        }
        catch ( Exception e )
        {
            throw new IllegalStateException( e );
        }
    }

    @Test
    @InjectMojo( goal = "install-file", pom = "classpath:/unit/install-file/with-pomFile-test.xml")
    public void testInstallFileWithPomFile( InstallFileMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        assignValuesForParameter( mojo );
        File pomFile = (File) getVariableValueFromObject( mojo, "pomFile" );

        ArtifactInstallerRequest request = execute( mojo );

        assertNotNull( request );
        Set<Artifact> artifacts = new HashSet<>( request.getArtifacts() );
        Artifact pom = getArtifact( null, "pom" );
        Artifact jar = getArtifact( null, "jar" );
        assertEquals( new HashSet<>( Arrays.asList( pom, jar ) ), artifacts );
        assertEquals( pomFile.toPath(), artifactManager.getPath( pom ).orElse( null ) );
        assertNotNull( artifactManager.getPath( jar ).orElse( null ) );
        assertEquals( LOCAL_REPO, request.getSession().getLocalRepository().getPath().toString() );
    }

    @Test
    @InjectMojo( goal = "install-file", pom = "classpath:/unit/install-file/with-pom-as-packaging.xml")
    public void testInstallFileWithPomAsPackaging( InstallFileMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        assignValuesForParameter( mojo );
        assertTrue( file.exists() );
        assertEquals( "pom", packaging );

        ArtifactInstallerRequest request = execute( mojo );

        assertNotNull( request );
        Set<Artifact> artifacts = new HashSet<>( request.getArtifacts() );
        Artifact pom = getArtifact( null, "pom" );
        assertEquals( new HashSet<>( Arrays.asList( pom ) ), artifacts );
    }

    @Test
    @InjectMojo( goal = "install-file", pom = "classpath:/unit/install-file/with-checksum.xml")
    public void testInstallFile( InstallFileMojo mojo )
        throws Exception
    {
        assertNotNull( mojo );
        assignValuesForParameter( mojo );

        ArtifactInstallerRequest request = execute( mojo );

        assertNotNull( request );
        Set<Artifact> artifacts = new HashSet<>( request.getArtifacts() );
        Artifact pom = getArtifact( null, "pom" );
        Artifact jar = getArtifact( null, "jar" );
        assertEquals( new HashSet<>( Arrays.asList( pom, jar ) ), artifacts );
        assertEquals( LOCAL_REPO, request.getSession().getLocalRepository().getPath().toString() );
    }

    private void assignValuesForParameter( Object obj )
        throws Exception
    {
        this.groupId = (String) getVariableValueFromObject( obj, "groupId" );
        this.artifactId = (String) getVariableValueFromObject( obj, "artifactId" );
        this.version = (String) getVariableValueFromObject( obj, "version" );
        this.packaging = (String) getVariableValueFromObject( obj, "packaging" );
        this.classifier = (String) getVariableValueFromObject( obj, "classifier" );
        this.file = (File) getVariableValueFromObject( obj, "file" );
    }

    private ArtifactStub getArtifact( String classifier, String extension )
    {
        return new ArtifactStub( groupId, artifactId, classifier != null ? classifier : "", version, extension );
    }

    private ArtifactInstallerRequest execute( InstallFileMojo mojo )
    {
        return execute( mojo, null );
    }

    private ArtifactInstallerRequest execute( InstallFileMojo mojo, Consumer<ArtifactInstallerRequest> consumer )
    {
        AtomicReference<ArtifactInstallerRequest> request = new AtomicReference<>();
        doAnswer( iom -> {
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

    private void assertFileExists( Path path )
    {
        assertTrue( path != null && Files.exists( path ), () -> path + " should exists" );
    }

    private void assertFileNotExists( Path path )
    {
        assertFalse( path != null && Files.exists( path ), () -> path + " should not exists" );
    }

    @Provides @Singleton @SuppressWarnings( "unused" )
    private Session createMavenSession()
    {
        return SessionStub.getMockSession( LOCAL_REPO );
    }

    @Provides
    private ArtifactInstaller createArtifactInstaller( Session session )
    {
        return session.getService( ArtifactInstaller.class );
    }

    @Provides
    private ArtifactManager createArtifactManager( Session session )
    {
        return session.getService( ArtifactManager.class );
    }
}

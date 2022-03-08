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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactInstaller;
import org.apache.maven.api.services.ArtifactInstallerException;
import org.apache.maven.api.services.ArtifactInstallerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;

/**
 * Installs the project's main artifact, and any other artifacts attached by other plugins in the lifecycle, to the
 * local repository.
 * 
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 */
@Mojo( name = "install", defaultPhase = LifecyclePhase.INSTALL )
public class InstallMojo
    extends AbstractInstallMojo
{

    /**
     * When building with multiple threads, reaching the last project doesn't have to mean that all projects are ready
     * to be installed
     */
    private static final AtomicInteger READYPROJECTSCOUNTER = new AtomicInteger();

    private static final List<ArtifactInstallerRequest> INSTALLREQUESTS =
        Collections.synchronizedList( new ArrayList<>() );

    /**
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true ) @SuppressWarnings( "unused" )
    private Project project;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    @SuppressWarnings( { "unused", "MismatchedQueryAndUpdateOfCollection" } )
    private List<Project> reactorProjects;

    /**
     * Whether every project should be installed during its own install-phase or at the end of the multimodule build. If
     * set to {@code true} and the build fails, none of the reactor projects is installed.
     * <strong>(experimental)</strong>
     * 
     * @since 2.5
     */
    @Parameter( defaultValue = "false", property = "installAtEnd" ) @SuppressWarnings( "unused" )
    private boolean installAtEnd;

    /**
     * Set this to <code>true</code> to bypass artifact installation. Use this for artifacts that do not need to be
     * installed in the local repository.
     * 
     * @since 2.4
     */
    @Parameter( property = "maven.install.skip", defaultValue = "false" )
    private boolean skip;

    @Component @SuppressWarnings( "unused" )
    private ArtifactInstaller installer;

    @Component @SuppressWarnings( "unused" )
    private ArtifactManager artifactManager;

    public void execute()
        throws MojoException
    {
        boolean addedInstallRequest = false;
        if ( skip )
        {
            logger.info( "Skipping artifact installation" );
        }
        else
        {
            List<Artifact> installables = new ArrayList<>();

            installables.add( project.getArtifact() );

            if ( !"pom".equals( project.getPackaging() ) )
            {
                Artifact pomArtifact = session.createArtifact(
                        project.getGroupId(), project.getArtifactId(), "",
                        project.getVersion(), "pom" );
                artifactManager.setPath( pomArtifact, project.getPomPath() );
                installables.add( pomArtifact );
            }

            ProjectManager projectManager = session.getService( ProjectManager.class );
            installables.addAll( projectManager.getAttachedArtifacts( project ) );

            for ( Artifact artifact : installables )
            {
                Path path = artifactManager.getPath( artifact ).orElse( null );
                if ( path == null )
                {
                    throw new MojoException( "The packaging for this project did not assign "
                            + "a file to the build artifact" );
                }
            }

            ArtifactInstallerRequest artifactInstallerRequest = ArtifactInstallerRequest.builder()
                        .session( session )
                        .artifacts( installables )
                        .build();

            if ( !installAtEnd )
            {
                installProject( artifactInstallerRequest );
            }
            else
            {
                INSTALLREQUESTS.add( artifactInstallerRequest );
                addedInstallRequest = true;
            }
        }

        boolean projectsReady = READYPROJECTSCOUNTER.incrementAndGet() == reactorProjects.size();
        if ( projectsReady )
        {
            synchronized ( INSTALLREQUESTS )
            {
                while ( !INSTALLREQUESTS.isEmpty() )
                {
                    installProject( INSTALLREQUESTS.remove( 0 ) );
                }
            }
        }
        else if ( addedInstallRequest )
        {
            logger.info( "Installing " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                + project.getVersion() + " at end" );
        }
    }

    private void installProject( ArtifactInstallerRequest pir )
        throws MojoException
    {
        try
        {
            installer.install( pir );
        }
        catch ( ArtifactInstallerException e )
        {
            throw new MojoException( "ArtifactInstallerRequest", e );
        }
    }

    public void setSkip( boolean skip )
    {
        this.skip = skip;
    }

}

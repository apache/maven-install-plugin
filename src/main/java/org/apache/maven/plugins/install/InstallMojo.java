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
import java.util.List;
import java.util.Map;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * Installs the project's main artifact, and any other artifacts attached by other plugins in the lifecycle, to the
 * local repository.
 * 
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 */
@Mojo( name = "install", defaultPhase = LifecyclePhase.INSTALL, threadSafe = true )
public class InstallMojo
        extends AbstractMojo
{
    @Component
    private RepositorySystem repositorySystem;

    @Parameter( defaultValue = "${session}", required = true, readonly = true )
    private MavenSession session;

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    private List<MavenProject> reactorProjects;

    @Parameter( defaultValue = "${plugin}", required = true, readonly = true )
    private PluginDescriptor pluginDescriptor;

    /**
     * Whether every project should be installed during its own install-phase or at the end of the multimodule build. If
     * set to {@code true} and the build fails, none of the reactor projects is installed.
     * <strong>(experimental)</strong>
     * 
     * @since 2.5
     */
    @Parameter( defaultValue = "false", property = "installAtEnd" )
    private boolean installAtEnd;

    /**
     * Set this to <code>true</code> to bypass artifact installation. Use this for artifacts that do not need to be
     * installed in the local repository.
     * 
     * @since 2.4
     */
    @Parameter( property = "maven.install.skip", defaultValue = "false" )
    private boolean skip;

    private enum State
    {
        SKIPPED, INSTALLED, TO_BE_INSTALLED
    }

    private static final String INSTALL_PROCESSED_MARKER = InstallMojo.class.getName() + ".processed";

    private void putState( State state )
    {
        getPluginContext().put( INSTALL_PROCESSED_MARKER, state.name() );
    }

    private State getState( MavenProject project )
    {
        Map<String, Object> pluginContext = session.getPluginContext( pluginDescriptor, project );
        return State.valueOf( (String) pluginContext.get( INSTALL_PROCESSED_MARKER ) );
    }

    private boolean hasState( MavenProject project )
    {
        Map<String, Object> pluginContext = session.getPluginContext( pluginDescriptor, project );
        return pluginContext.containsKey( INSTALL_PROCESSED_MARKER );
    }

    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( skip )
        {
            getLog().info( "Skipping artifact installation" );
            putState( State.SKIPPED );
        }
        else
        {
            if ( !installAtEnd )
            {
                installProject( project );
                putState( State.INSTALLED );
            }
            else
            {
                getLog().info( "Deferring install for " + getProjectReferenceId( project ) + " at end" );
                putState( State.TO_BE_INSTALLED );
            }
        }

        if ( allProjectsMarked() )
        {
            for ( MavenProject reactorProject : reactorProjects )
            {
                State state = getState( reactorProject );
                if ( state == State.TO_BE_INSTALLED )
                {
                    installProject( reactorProject );
                }
            }
        }
    }

    private String getProjectReferenceId( MavenProject mavenProject )
    {
        return mavenProject.getGroupId() + ":" + mavenProject.getArtifactId() + ":" + mavenProject.getVersion();
    }

    private boolean allProjectsMarked()
    {
        for ( MavenProject reactorProject : reactorProjects )
        {
            if ( !hasState( reactorProject ) )
            {
                return false;
            }
        }
        return true;
    }

    private void installProject( MavenProject project ) throws MojoExecutionException, MojoFailureException
    {
        try
        {
            repositorySystem.install( session.getRepositorySession(), processProject( project ) );
        }
        catch ( IllegalArgumentException e )
        {
            throw new MojoFailureException( e.getMessage(), e );
        }
        catch ( InstallationException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }

    /**
     * Processes passed in {@link MavenProject} and produces {@link InstallRequest} out of it.
     *
     * @throws IllegalArgumentException if project is badly set up.
     */
    private InstallRequest processProject( MavenProject project )
    {
        InstallRequest request = new InstallRequest();
        org.apache.maven.artifact.Artifact mavenMainArtifact = project.getArtifact();
        String packaging = project.getPackaging();
        File pomFile = project.getFile();
        boolean isPomArtifact = "pom".equals( packaging );
        boolean pomArtifactAttached = false;

        if ( pomFile != null )
        {
            request.addArtifact( RepositoryUtils.toArtifact( new ProjectArtifact( project ) ) );
            pomArtifactAttached = true;
        }

        if ( !isPomArtifact )
        {
            File file = mavenMainArtifact.getFile();
            if ( file != null && file.isFile() )
            {
                Artifact mainArtifact = RepositoryUtils.toArtifact( mavenMainArtifact );
                request.addArtifact( mainArtifact );

                if ( !pomArtifactAttached )
                {
                    for ( Object metadata : mavenMainArtifact.getMetadataList() )
                    {
                        if ( metadata instanceof ProjectArtifactMetadata )
                        {
                            request.addArtifact( new SubArtifact(
                                    mainArtifact,
                                    "",
                                    "pom"
                            ).setFile( ( (ProjectArtifactMetadata) metadata ).getFile() ) );
                            pomArtifactAttached = true;
                        }
                    }
                }
            }
            else if ( !project.getAttachedArtifacts().isEmpty() )
            {
                throw new IllegalArgumentException( "The packaging plugin for this project did not assign "
                        + "a main file to the project but it has attachments. Change packaging to 'pom'." );
            }
            else
            {
                throw new IllegalArgumentException( "The packaging for this project did not assign "
                        + "a file to the build artifact" );
            }
        }

        if ( !pomArtifactAttached )
        {
            throw new IllegalArgumentException( "The POM could not be attached" );
        }

        for ( org.apache.maven.artifact.Artifact attached : project.getAttachedArtifacts() )
        {
            getLog().debug( "Attaching for install: " + attached.getId() );
            request.addArtifact( RepositoryUtils.toArtifact( attached ) );
        }

        return request;
    }

}

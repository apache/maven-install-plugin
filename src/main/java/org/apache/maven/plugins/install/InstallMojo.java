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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.transfer.artifact.install.ArtifactInstallerException;
import org.apache.maven.shared.transfer.project.NoFileAssignedException;
import org.apache.maven.shared.transfer.project.install.ProjectInstaller;
import org.apache.maven.shared.transfer.project.install.ProjectInstallerRequest;

/**
 * Installs the project's main artifact, and any other artifacts attached by other plugins in the lifecycle, to the
 * local repository.
 * 
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 */
@Mojo( name = "install", defaultPhase = LifecyclePhase.INSTALL, threadSafe = true )
public class InstallMojo
    extends AbstractInstallMojo
{
    private static final String INSTALL_PROCESSED_MARKER = InstallMojo.class.getName() + ".processed";

    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private MavenProject project;

    @Parameter( defaultValue = "${reactorProjects}", required = true, readonly = true )
    private List<MavenProject> reactorProjects;

    @Parameter( defaultValue = "${session}", required = true, readonly = true )
    private MavenSession session;

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

    @Component
    private ProjectInstaller installer;

    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        boolean addedInstallRequest = false;
        if ( skip )
        {
            getPluginContext().put( INSTALL_PROCESSED_MARKER, Boolean.FALSE );
            getLog().info( "Skipping artifact installation" );
        }
        else
        {
            if ( !installAtEnd )
            {
                installProject( project );
            }
            else
            {
                getPluginContext().put( INSTALL_PROCESSED_MARKER, Boolean.TRUE );
                addedInstallRequest = true;
            }
        }

        if ( allProjectsMarked() )
        {
            for ( MavenProject reactorProject : reactorProjects )
            {
                Map<String, Object> pluginContext = session.getPluginContext( pluginDescriptor, reactorProject );
                Boolean install = (Boolean) pluginContext.get( INSTALL_PROCESSED_MARKER );
                if ( !install )
                {
                    getLog().info(
                        "Project " + getProjectReferenceId( reactorProject ) + " skipped install"
                    );
                }
                else
                {
                    installProject( reactorProject );
                }
            }
        }
        else if ( addedInstallRequest )
        {
            getLog().info( "Installing " + getProjectReferenceId( project ) + " at end" );
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
            Map<String, Object> pluginContext = session.getPluginContext( pluginDescriptor, reactorProject );
            if ( !pluginContext.containsKey( INSTALL_PROCESSED_MARKER ) )
            {
                return false;
            }
        }
        return true;
    }

    private void installProject( MavenProject pir )
        throws MojoFailureException, MojoExecutionException
    {
        try
        {
            installer.install( session.getProjectBuildingRequest(), new ProjectInstallerRequest().setProject( pir ) );
        }
        catch ( IOException e )
        {
            throw new MojoFailureException( "IOException", e );
        }
        catch ( ArtifactInstallerException e )
        {
            throw new MojoExecutionException( "ArtifactInstallerException", e );
        }
        catch ( NoFileAssignedException e )
        {
            throw new MojoExecutionException( "NoFileAssignedException", e );
        }

    }

    public void setSkip( boolean skip )
    {
        this.skip = skip;
    }

}

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

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * Common fields for installation mojos.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public abstract class AbstractInstallMojo
    extends AbstractMojo
{
    @Component
    protected RepositorySystem repositorySystem;

    @Parameter( defaultValue = "${session}", required = true, readonly = true )
    protected MavenSession session;

    /**
     * Gets the path of the specified artifact within the local repository. Note that the returned path need not exist
     * (yet).
     *
     * @param artifact The artifact whose local repo path should be determined, must not be <code>null</code>.
     * @return The absolute path to the artifact when installed, never <code>null</code>.
     */
    protected File getLocalRepoFile( Artifact artifact )
    {
        String path = session.getRepositorySession().getLocalRepositoryManager()
                .getPathForLocalArtifact( RepositoryUtils.toArtifact( artifact ) );
        return new File( session.getRepositorySession().getLocalRepository().getBasedir(), path );
    }

    /**
     * Gets the path of the specified artifact metadata within the local repository. Note that the returned path need
     * not exist (yet).
     *
     * @param metadata The artifact metadata whose local repo path should be determined, must not be <code>null</code>.
     * @return The absolute path to the artifact metadata when installed, never <code>null</code>.
     */
    protected File getLocalRepoFile( ProjectArtifactMetadata metadata )
    {
        DefaultArtifact pomArtifact = new DefaultArtifact(
                metadata.getGroupId(),
                metadata.getArtifactId(),
                "",
                "pom",
                metadata.getBaseVersion() );

        String path = session.getRepositorySession().getLocalRepositoryManager().getPathForLocalArtifact(
                pomArtifact );
        return new File( session.getRepositorySession().getLocalRepository().getBasedir(), path );
    }

    protected void installProject( MavenProject project )
            throws MojoFailureException, MojoExecutionException
    {
        try
        {
            InstallRequest request = new InstallRequest();
            Artifact artifact = project.getArtifact();
            String packaging = project.getPackaging();
            File pomFile = project.getFile();
            boolean isPomArtifact = "pom".equals( packaging );

            if ( pomFile != null )
            {
                request.addArtifact( RepositoryUtils.toArtifact( new ProjectArtifact( project ) ) );
            }

            if ( !isPomArtifact )
            {
                File file = artifact.getFile();

                // Here, we have a temporary solution to MINSTALL-3 (isDirectory() is true if it went through compile
                // but not package). We are designing in a proper solution for Maven 2.1
                if ( file != null && file.isFile() )
                {
                    org.eclipse.aether.artifact.Artifact mainArtifact = RepositoryUtils.toArtifact( artifact );
                    request.addArtifact( mainArtifact );

                    for ( Object metadata : artifact.getMetadataList() )
                    {
                        if ( metadata instanceof ProjectArtifactMetadata )
                        {
                            org.eclipse.aether.artifact.Artifact pomArtifact =
                                    new SubArtifact( mainArtifact, "", "pom" );
                            pomArtifact = pomArtifact.setFile( ( (ProjectArtifactMetadata) metadata ).getFile() );
                            request.addArtifact( pomArtifact );
                        }
                    }
                }
                else if ( !project.getAttachedArtifacts().isEmpty() )
                {
                    throw new MojoExecutionException( "The packaging plugin for this project did not assign "
                            + "a main file to the project but it has attachments. Change packaging to 'pom'." );
                }
                else
                {
                    throw new MojoExecutionException( "The packaging for this project did not assign "
                            + "a file to the build artifact" );
                }
            }

            for ( Artifact attached : project.getAttachedArtifacts() )
            {
                getLog().debug( "Attaching for install: " + attached.getId() );
                request.addArtifact( RepositoryUtils.toArtifact( attached ) );
            }

            repositorySystem.install( session.getRepositorySession(), request );
        }
        catch ( InstallationException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }
    }
}

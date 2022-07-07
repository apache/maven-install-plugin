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

import javax.inject.Named;
import javax.inject.Singleton;

import java.io.File;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.project.artifact.ProjectArtifactMetadata;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.util.artifact.SubArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Installer component.
 */
@Singleton
@Named
public class Installer
{
    private final Logger logger = LoggerFactory.getLogger( getClass() );

    /**
     * Gets the path of the specified artifact within the local repository. Note that the returned path need not exist
     * (yet).
     *
     * @param artifact The artifact whose local repo path should be determined, must not be <code>null</code>.
     * @return The absolute path to the artifact when installed, never <code>null</code>.
     */
    public File getLocalRepositoryFile( RepositorySystemSession session, Artifact artifact )
    {
        String path = session.getLocalRepositoryManager().getPathForLocalArtifact( artifact );
        return new File( session.getLocalRepository().getBasedir(), path );
    }

    /**
     * Gets the path of the specified artifact POM within the local repository. Note that the returned path need
     * not exist (yet).
     *
     * @param artifact The artifact whose POM local repo path should be determined, must not be <code>null</code>.
     * @return The absolute path to the artifact POM when installed, never <code>null</code>.
     */
    public File getPomLocalRepositoryFile( RepositorySystemSession session, Artifact artifact )
    {
        SubArtifact pomArtifact = new SubArtifact( artifact, "", "pom" );
        String path = session.getLocalRepositoryManager().getPathForLocalArtifact( pomArtifact );
        return new File( session.getLocalRepository().getBasedir(), path );
    }

    /**
     * Processes passed in {@link MavenProject} and produces {@link InstallRequest} out of it.
     *
     * @throws IllegalArgumentException if project is badly set up.
     */
    public InstallRequest processProject( MavenProject project )
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
            logger.debug( "Attaching for install: " + attached.getId() );
            request.addArtifact( RepositoryUtils.toArtifact( attached ) );
        }

        return request;
    }

    public boolean isValidId( String id )
    {
        if ( id == null )
        {
            return false;
        }
        for ( int i = 0; i < id.length(); i++ )
        {
            char c = id.charAt( i );
            if ( !isValidIdCharacter( c ) )
            {
                return false;
            }
        }
        return true;
    }


    private boolean isValidIdCharacter( char c )
    {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '-' || c == '_' || c == '.';
    }

    private static final String ILLEGAL_VERSION_CHARS = "\\/:\"<>|?*[](){},";

    public boolean isValidVersion( String version )
    {
        if ( version == null )
        {
            return false;
        }
        for ( int i = version.length() - 1; i >= 0; i-- )
        {
            if ( ILLEGAL_VERSION_CHARS.indexOf( version.charAt( i ) ) >= 0 )
            {
                return false;
            }
        }
        return true;
    }

}

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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactFactory;
import org.apache.maven.api.services.ArtifactFactoryRequest;
import org.apache.maven.api.services.ArtifactInstaller;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.shared.utils.WriterFactory;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Installs a file in the local repository.
 * 
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
@Mojo( name = "install-file", requiresProject = false, aggregator = true )
public class InstallFileMojo
    extends AbstractInstallMojo
{

    /**
     * GroupId of the artifact to be installed. Retrieved from POM file if one is specified or extracted from
     * {@code pom.xml} in jar if available.
     */
    @Parameter( property = "groupId" )
    private String groupId;

    /**
     * ArtifactId of the artifact to be installed. Retrieved from POM file if one is specified or extracted from
     * {@code pom.xml} in jar if available.
     */
    @Parameter( property = "artifactId" )
    private String artifactId;

    /**
     * Version of the artifact to be installed. Retrieved from POM file if one is specified or extracted from
     * {@code pom.xml} in jar if available.
     */
    @Parameter( property = "version" )
    private String version;

    /**
     * Packaging type of the artifact to be installed. Retrieved from POM file if one is specified or extracted from
     * {@code pom.xml} in jar if available.
     */
    @Parameter( property = "packaging" )
    private String packaging;

    /**
     * Classifier type of the artifact to be installed. For example, "sources" or "javadoc". Defaults to none which
     * means this is the project's main artifact.
     * 
     * @since 2.2
     */
    @Parameter( property = "classifier" )
    private String classifier;

    /**
     * The file to be installed in the local repository.
     */
    @Parameter( property = "file", required = true )
    private File file;

    /**
     * The bundled API docs for the artifact.
     * 
     * @since 2.3
     */
    @Parameter( property = "javadoc" )
    private File javadoc;

    /**
     * The bundled sources for the artifact.
     * 
     * @since 2.3
     */
    @Parameter( property = "sources" )
    private File sources;

    /**
     * Location of an existing POM file to be installed alongside the main artifact, given by the {@link #file}
     * parameter.
     * 
     * @since 2.1
     */
    @Parameter( property = "pomFile" )
    private File pomFile;

    /**
     * Generate a minimal POM for the artifact if none is supplied via the parameter {@link #pomFile}. Defaults to
     * <code>true</code> if there is no existing POM in the local repository yet.
     * 
     * @since 2.1
     */
    @Parameter( property = "generatePom" )
    private Boolean generatePom;

    /**
     * The path for a specific local repository directory. If not specified the local repository path configured in the
     * Maven settings will be used.
     * 
     * @since 2.2
     */
    @Parameter( property = "localRepositoryPath" )
    private File localRepositoryPath;

    /**
     * Used to install the project created.
     */
    @Component
    private ArtifactInstaller installer;

    @Component
    private ArtifactManager artifactManager;

    /**
     * @see org.apache.maven.api.plugin.Mojo#execute()
     */
    public void execute()
        throws MojoException
    {

        if ( !file.exists() )
        {
            String message = "The specified file '" + file.getPath() + "' does not exist";
            logger.error( message );
            throw new MojoException( message );
        }

        Session session = this.session;

        List<Artifact> installableArtifacts = new ArrayList<>();

        // ----------------------------------------------------------------------
        // Override the default localRepository variable
        // ----------------------------------------------------------------------
        if ( localRepositoryPath != null )
        {
            session = session.withLocalRepository( session.createLocalRepository( localRepositoryPath.toPath() ) );

            logger.debug( "localRepoPath: {}", localRepositoryPath );
        }

        File temporaryPom = null;

        if ( pomFile != null )
        {
            processModel( readModel( pomFile ) );
        }
        else
        {
            temporaryPom = readingPomFromJarFile();
            pomFile = temporaryPom;
        }

        // We need to set a new ArtifactHandler otherwise
        // the extension will be set to the packaging type
        // which is sometimes wrong.
        Artifact artifact = session.getService( ArtifactFactory.class )
                .create( ArtifactFactoryRequest.builder()
                        .session( session )
                        .groupId( groupId )
                        .artifactId( artifactId )
                        .classifier( classifier )
                        .version( version )
                        .extension( FileUtils.getExtension( file.getName() ) )
                        .type( packaging )
                        .build() );

        if ( file.equals( getLocalRepoFile( artifact ) ) )
        {
            throw new MojoException( "Cannot install artifact. "
                + "Artifact is already in the local repository.\n\nFile in question is: " + file + "\n" );
        }

        artifactManager.setPath( artifact, file.toPath() );
        installableArtifacts.add( artifact );

        if ( !"pom".equals( packaging ) )
        {
            Artifact pomArtifact = session.getService( ArtifactFactory.class )
                    .create( ArtifactFactoryRequest.builder()
                            .session( session )
                            .groupId( groupId )
                            .artifactId( artifactId )
                            .classifier( classifier )
                            .version( version )
                            .type( "pom" )
                            .build() );
            if ( pomFile != null )
            {
                artifactManager.setPath( pomArtifact, pomFile.toPath() );
                installableArtifacts.add( artifact );
            }
            else
            {
                temporaryPom = generatePomFile();
                artifactManager.setPath( pomArtifact, temporaryPom.toPath() );
                if ( Boolean.TRUE.equals( generatePom )
                    || ( generatePom == null && !getLocalRepoFile( pomArtifact ).exists() ) )
                {
                    logger.debug( "Installing generated POM" );
                    installableArtifacts.add( artifact );
                }
                else if ( generatePom == null )
                {
                    logger.debug( "Skipping installation of generated POM, already present in local repository" );
                }
            }
        }

        if ( sources != null )
        {
            Artifact sourcesArtifact = session.getService( ArtifactFactory.class )
                    .create( ArtifactFactoryRequest.builder()
                            .session( session )
                            .groupId( groupId )
                            .artifactId( artifactId )
                            .classifier( "sources" )
                            .version( version )
                            .type( "jar" )
                            .build() );
            artifactManager.setPath( sourcesArtifact, sources.toPath() );
            installableArtifacts.add( artifact );
        }

        if ( javadoc != null )
        {
            Artifact sourcesArtifact = session.getService( ArtifactFactory.class )
                    .create( ArtifactFactoryRequest.builder()
                            .session( session )
                            .groupId( groupId )
                            .artifactId( artifactId )
                            .classifier( "javadoc" )
                            .version( version )
                            .type( "jar" )
                            .build() );
            artifactManager.setPath( sourcesArtifact, javadoc.toPath() );
            installableArtifacts.add( artifact );
        }

        try
        {
            installer.install( session, installableArtifacts );
        }
        catch ( Exception e )
        {
            throw new MojoException( e.getMessage(), e );
        }
        finally
        {
            if ( temporaryPom != null )
            {
                // noinspection ResultOfMethodCallIgnored
                temporaryPom.delete();
            }
        }
    }


    private File readingPomFromJarFile()
        throws MojoException
    {

        File pomFile = null;

        try ( JarFile jarFile = new JarFile( file ) )
        {
            Pattern pomEntry = Pattern.compile( "META-INF/maven/.*/pom\\.xml" );

            JarEntry entry = jarFile.stream()
                    .filter( e -> pomEntry.matcher( e.getName() ).matches() )
                    .findFirst().orElse( null );

            if ( entry != null )
            {
                logger.debug( "Using " + entry.getName() + " as pomFile" );

                String base = file.getName();
                if ( base.indexOf( '.' ) > 0 )
                {
                    base = base.substring( 0, base.lastIndexOf( '.' ) );
                }
                pomFile = File.createTempFile( base, ".pom" );

                try ( InputStream pomInputStream = jarFile.getInputStream( entry ) )
                {
                    Files.copy( pomInputStream, pomFile.toPath() );
                }

                processModel( readModel( pomFile ) );
            }

            if ( pomFile == null )
            {
                logger.info( "pom.xml not found in " + file.getName() );
            }
        }
        catch ( IOException e )
        {
            // ignore, artifact not packaged by Maven
        }
        return pomFile;
    }

    /**
     * Parses a POM.
     * 
     * @param pomFile The path of the POM file to parse, must not be <code>null</code>.
     * @return The model from the POM file, never <code>null</code>.
     * @throws MojoException If the POM could not be parsed.
     */
    private Model readModel( File pomFile )
        throws MojoException
    {
        try ( Reader reader = new XmlStreamReader( pomFile ) )
        {
            final Model model = new MavenXpp3Reader().read( reader );
            return model;
        }
        catch ( FileNotFoundException e )
        {
            throw new MojoException( "File not found " + pomFile, e );
        }
        catch ( IOException e )
        {
            throw new MojoException( "Error reading POM " + pomFile, e );
        }
        catch ( XmlPullParserException e )
        {
            throw new MojoException( "Error parsing POM " + pomFile, e );
        }
    }

    /**
     * Populates missing mojo parameters from the specified POM.
     * 
     * @param model The POM to extract missing artifact coordinates from, must not be <code>null</code>.
     */
    private void processModel( Model model )
    {
        Parent parent = model.getParent();

        if ( this.groupId == null )
        {
            this.groupId = model.getGroupId();
            if ( this.groupId == null && parent != null )
            {
                this.groupId = parent.getGroupId();
            }
        }
        if ( this.artifactId == null )
        {
            this.artifactId = model.getArtifactId();
        }
        if ( this.version == null )
        {
            this.version = model.getVersion();
            if ( this.version == null && parent != null )
            {
                this.version = parent.getVersion();
            }
        }
        if ( this.packaging == null )
        {
            this.packaging = model.getPackaging();
        }
    }

    /**
     * Generates a minimal model from the user-supplied artifact information.
     * 
     * @return The generated model, never <code>null</code>.
     */
    private Model generateModel()
    {
        Model model = new Model();

        model.setModelVersion( "4.0.0" );

        model.setGroupId( groupId );
        model.setArtifactId( artifactId );
        model.setVersion( version );
        model.setPackaging( packaging );

        model.setDescription( "POM was created from install:install-file" );

        return model;
    }

    /**
     * Generates a (temporary) POM file from the plugin configuration. It's the responsibility of the caller to delete
     * the generated file when no longer needed.
     * 
     * @return The path to the generated POM file, never <code>null</code>.
     * @throws MojoException If the POM file could not be generated.
     */
    private File generatePomFile()
        throws MojoException
    {
        try
        {
            Model model = generateModel();
            File pomFile = File.createTempFile( "mvninstall", ".pom" );
            try ( Writer writer = WriterFactory.newXmlWriter( pomFile ) )
            {
                new MavenXpp3Writer().write( writer, model );
            }
            return pomFile;
        }
        catch ( IOException e )
        {
            throw new MojoException( "Error writing temporary POM file: " + e.getMessage(), e );
        }
    }

}

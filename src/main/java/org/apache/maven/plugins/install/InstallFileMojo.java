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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.XmlStreamWriter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.aether.DefaultRepositoryCache;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.util.artifact.SubArtifact;

/**
 * Installs a file in the local repository.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
@Mojo(name = "install-file", requiresProject = false, aggregator = true, threadSafe = true)
public class InstallFileMojo extends AbstractMojo {
    private static final String LS = System.getProperty("line.separator");

    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    /**
     * GroupId of the artifact to be installed. Retrieved from POM file if one is specified or extracted from
     * {@code pom.xml} in jar if available.
     */
    @Parameter(property = "groupId")
    private String groupId;

    /**
     * ArtifactId of the artifact to be installed. Retrieved from POM file if one is specified or extracted from
     * {@code pom.xml} in jar if available.
     */
    @Parameter(property = "artifactId")
    private String artifactId;

    /**
     * Version of the artifact to be installed. Retrieved from POM file if one is specified or extracted from
     * {@code pom.xml} in jar if available.
     */
    @Parameter(property = "version")
    private String version;

    /**
     * Packaging type of the artifact to be installed. Retrieved from POM file if one is specified or extracted from
     * {@code pom.xml} in jar if available.
     */
    @Parameter(property = "packaging")
    private String packaging;

    /**
     * Classifier type of the artifact to be installed. For example, "sources" or "javadoc". Defaults to none which
     * means this is the project's main artifact.
     *
     * @since 2.2
     */
    @Parameter(property = "classifier")
    private String classifier;

    /**
     * The file to be installed in the local repository.
     */
    @Parameter(property = "file", required = true)
    private File file;

    /**
     * The bundled API docs for the artifact.
     *
     * @since 2.3
     */
    @Parameter(property = "javadoc")
    private File javadoc;

    /**
     * The bundled sources for the artifact.
     *
     * @since 2.3
     */
    @Parameter(property = "sources")
    private File sources;

    /**
     * Location of an existing POM file to be installed alongside the main artifact, given by the {@link #file}
     * parameter.
     *
     * @since 2.1
     */
    @Parameter(property = "pomFile")
    private File pomFile;

    /**
     * Generate a minimal POM for the artifact if none is supplied via the parameter {@link #pomFile}. Defaults to
     * <code>true</code> if there is no existing POM in the local repository yet.
     *
     * @since 2.1
     */
    @Parameter(property = "generatePom")
    private Boolean generatePom;

    /**
     * The path for a specific local repository directory. If not specified the local repository path configured in the
     * Maven settings will be used.
     *
     * @since 2.2
     */
    @Parameter(property = "localRepositoryPath")
    private File localRepositoryPath;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!file.exists()) {
            String message = "The specified file '" + file.getPath() + "' does not exist";
            getLog().error(message);
            throw new MojoFailureException(message);
        }

        RepositorySystemSession repositorySystemSession = session.getRepositorySession();
        if (localRepositoryPath != null) {
            // "clone" repository session and replace localRepository
            DefaultRepositorySystemSession newSession =
                    new DefaultRepositorySystemSession(session.getRepositorySession());
            // Clear cache, since we're using a new local repository
            newSession.setCache(new DefaultRepositoryCache());
            // keep same repositoryType
            String contentType = newSession.getLocalRepository().getContentType();
            if ("enhanced".equals(contentType)) {
                contentType = "default";
            }
            LocalRepositoryManager localRepositoryManager = repositorySystem.newLocalRepositoryManager(
                    newSession, new LocalRepository(localRepositoryPath, contentType));
            newSession.setLocalRepositoryManager(localRepositoryManager);
            repositorySystemSession = newSession;
            getLog().debug("localRepoPath: "
                    + localRepositoryManager.getRepository().getBasedir());
        }

        File temporaryPom = null;

        if (pomFile != null) {
            processModel(readModel(pomFile));
        } else {
            temporaryPom = readingPomFromJarFile();
            if (!Boolean.TRUE.equals(generatePom)) {
                pomFile = temporaryPom;
                getLog().debug("Using JAR embedded POM as pomFile");
            }
        }

        if (groupId == null || artifactId == null || version == null || packaging == null) {
            throw new MojoExecutionException("The artifact information is incomplete: 'groupId', 'artifactId', "
                    + "'version' and 'packaging' are required.");
        }

        if (!isValidId(groupId) || !isValidId(artifactId) || !isValidVersion(version)) {
            throw new MojoExecutionException("The artifact information is not valid: uses invalid characters.");
        }

        InstallRequest installRequest = new InstallRequest();

        boolean isFilePom = classifier == null && "pom".equals(packaging);
        if (!isFilePom) {
            ArtifactType artifactType =
                    repositorySystemSession.getArtifactTypeRegistry().get(packaging);
            if (artifactType != null
                    && StringUtils.isEmpty(classifier)
                    && !StringUtils.isEmpty(artifactType.getClassifier())) {
                classifier = artifactType.getClassifier();
            }
        }
        Artifact mainArtifact = new DefaultArtifact(
                        groupId, artifactId, classifier, isFilePom ? "pom" : getExtension(file), version)
                .setFile(file);
        installRequest.addArtifact(mainArtifact);

        File artifactLocalFile = getLocalRepositoryFile(repositorySystemSession, mainArtifact);
        File pomLocalFile = getPomLocalRepositoryFile(repositorySystemSession, mainArtifact);

        if (file.equals(artifactLocalFile)) {
            throw new MojoFailureException("Cannot install artifact. " + "Artifact is already in the local repository."
                    + LS + LS + "File in question is: " + file + LS);
        }

        if (!"pom".equals(packaging)) {
            if (pomFile != null) {
                installRequest.addArtifact(new SubArtifact(mainArtifact, "", "pom", pomFile));
            } else {
                if (Boolean.TRUE.equals(generatePom) || (generatePom == null && !pomLocalFile.exists())) {
                    temporaryPom = generatePomFile();
                    getLog().debug("Installing generated POM");
                    installRequest.addArtifact(new SubArtifact(mainArtifact, "", "pom", temporaryPom));
                } else if (generatePom == null) {
                    getLog().debug("Skipping installation of generated POM, already present in local repository");
                }
            }
        }

        if (sources != null) {
            installRequest.addArtifact(new SubArtifact(mainArtifact, "sources", "jar", sources));
        }

        if (javadoc != null) {
            installRequest.addArtifact(new SubArtifact(mainArtifact, "javadoc", "jar", javadoc));
        }

        try {
            repositorySystem.install(repositorySystemSession, installRequest);
        } catch (InstallationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            if (temporaryPom != null) {
                // noinspection ResultOfMethodCallIgnored
                temporaryPom.delete();
            }
        }
    }

    private File readingPomFromJarFile() throws MojoExecutionException {
        File pomFile = null;

        JarFile jarFile = null;
        try {
            Pattern pomEntry = Pattern.compile("META-INF/maven/.*/pom\\.xml");

            jarFile = new JarFile(file);

            Enumeration<JarEntry> jarEntries = jarFile.entries();

            while (jarEntries.hasMoreElements()) {
                JarEntry entry = jarEntries.nextElement();

                if (pomEntry.matcher(entry.getName()).matches()) {
                    getLog().debug("Loading " + entry.getName());

                    InputStream pomInputStream = null;
                    OutputStream pomOutputStream = null;

                    try {
                        pomInputStream = jarFile.getInputStream(entry);

                        String base = file.getName();
                        if (base.indexOf('.') > 0) {
                            base = base.substring(0, base.lastIndexOf('.'));
                        }
                        pomFile = File.createTempFile(base, ".pom");

                        pomOutputStream = Files.newOutputStream(pomFile.toPath());

                        IOUtil.copy(pomInputStream, pomOutputStream);

                        pomOutputStream.close();
                        pomOutputStream = null;

                        pomInputStream.close();
                        pomInputStream = null;

                        processModel(readModel(pomFile));

                        break;
                    } finally {
                        IOUtil.close(pomInputStream);
                        IOUtil.close(pomOutputStream);
                    }
                }
            }

            if (pomFile == null) {
                getLog().info("pom.xml not found in " + file.getName());
            }
        } catch (IOException e) {
            // ignore, artifact not packaged by Maven
        } finally {
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    // we did our best
                }
            }
        }
        return pomFile;
    }

    /**
     * Parses a POM.
     *
     * @param pomFile The path of the POM file to parse, must not be <code>null</code>.
     * @return The model from the POM file, never <code>null</code>.
     * @throws MojoExecutionException If the POM could not be parsed.
     */
    private Model readModel(File pomFile) throws MojoExecutionException {
        Reader reader = null;
        try {
            reader = new XmlStreamReader(pomFile);
            final Model model = new MavenXpp3Reader().read(reader);
            reader.close();
            reader = null;
            return model;
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("File not found " + pomFile, e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading POM " + pomFile, e);
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException("Error parsing POM " + pomFile, e);
        } finally {
            IOUtil.close(reader);
        }
    }

    /**
     * Populates missing mojo parameters from the specified POM.
     *
     * @param model The POM to extract missing artifact coordinates from, must not be <code>null</code>.
     */
    private void processModel(Model model) {
        Parent parent = model.getParent();

        if (this.groupId == null) {
            this.groupId = model.getGroupId();
            if (this.groupId == null && parent != null) {
                this.groupId = parent.getGroupId();
            }
        }
        if (this.artifactId == null) {
            this.artifactId = model.getArtifactId();
        }
        if (this.version == null) {
            this.version = model.getVersion();
            if (this.version == null && parent != null) {
                this.version = parent.getVersion();
            }
        }
        if (this.packaging == null) {
            this.packaging = model.getPackaging();
        }
    }

    /**
     * Generates a minimal model from the user-supplied artifact information.
     *
     * @return The generated model, never <code>null</code>.
     */
    private Model generateModel() {
        Model model = new Model();

        model.setModelVersion("4.0.0");

        model.setGroupId(groupId);
        model.setArtifactId(artifactId);
        model.setVersion(version);
        model.setPackaging(packaging);

        model.setDescription("POM was created from install:install-file");

        return model;
    }

    /**
     * Generates a (temporary) POM file from the plugin configuration. It's the responsibility of the caller to delete
     * the generated file when no longer needed.
     *
     * @return The path to the generated POM file, never <code>null</code>.
     * @throws MojoExecutionException If the POM file could not be generated.
     */
    private File generatePomFile() throws MojoExecutionException {
        Model model = generateModel();

        Writer writer = null;
        try {
            File pomFile = File.createTempFile("mvninstall", ".pom");

            writer = new XmlStreamWriter(pomFile);
            new MavenXpp3Writer().write(writer, model);
            writer.close();
            writer = null;

            return pomFile;
        } catch (IOException e) {
            throw new MojoExecutionException("Error writing temporary POM file: " + e.getMessage(), e);
        } finally {
            IOUtil.close(writer);
        }
    }

    /**
     * Gets the path of the specified artifact within the local repository. Note that the returned path need not exist
     * (yet).
     */
    private File getLocalRepositoryFile(RepositorySystemSession session, Artifact artifact) {
        String path = session.getLocalRepositoryManager().getPathForLocalArtifact(artifact);
        return new File(session.getLocalRepository().getBasedir(), path);
    }

    /**
     * Gets the path of the specified artifact POM within the local repository. Note that the returned path need
     * not exist (yet).
     */
    private File getPomLocalRepositoryFile(RepositorySystemSession session, Artifact artifact) {
        SubArtifact pomArtifact = new SubArtifact(artifact, "", "pom");
        String path = session.getLocalRepositoryManager().getPathForLocalArtifact(pomArtifact);
        return new File(session.getLocalRepository().getBasedir(), path);
    }

    // these below should be shared (duplicated in m-install-p, m-deploy-p)

    /**
     * Specialization of {@link FileUtils#getExtension(String)} that honors various {@code tar.xxx} combinations.
     */
    private String getExtension(final File file) {
        String filename = file.getName();
        if (filename.contains(".tar.")) {
            return "tar." + FileUtils.getExtension(filename);
        } else {
            return FileUtils.getExtension(filename);
        }
    }

    /**
     * Returns {@code true} if passed in string is "valid Maven ID" (groupId or artifactId).
     */
    private boolean isValidId(String id) {
        if (id == null) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            if (!(c >= 'a' && c <= 'z'
                    || c >= 'A' && c <= 'Z'
                    || c >= '0' && c <= '9'
                    || c == '-'
                    || c == '_'
                    || c == '.')) {
                return false;
            }
        }
        return true;
    }

    private static final String ILLEGAL_VERSION_CHARS = "\\/:\"<>|?*[](){},";

    /**
     * Returns {@code true} if passed in string is "valid Maven (simple. non range, expression, etc) version".
     */
    private boolean isValidVersion(String version) {
        if (version == null) {
            return false;
        }
        for (int i = version.length() - 1; i >= 0; i--) {
            if (ILLEGAL_VERSION_CHARS.indexOf(version.charAt(i)) >= 0) {
                return false;
            }
        }
        return true;
    }
}

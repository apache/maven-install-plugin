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
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.model.Parent;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactInstaller;
import org.apache.maven.api.services.ArtifactInstallerException;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.xml.ModelXmlFactory;
import org.apache.maven.api.services.xml.XmlReaderException;

/**
 * Installs a file in the local repository.
 */
@Mojo(name = "install-file", projectRequired = false, aggregator = true)
@SuppressWarnings("unused")
public class InstallFileMojo implements org.apache.maven.api.plugin.Mojo {
    private static final String TAR = "tar.";
    private static final String ILLEGAL_VERSION_CHARS = "\\/:\"<>|?*[](){},";

    @Inject
    private Log log;

    @Inject
    private Session session;

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
    private Path file;

    /**
     * The bundled API docs for the artifact.
     *
     * @since 2.3
     */
    @Parameter(property = "javadoc")
    private Path javadoc;

    /**
     * The bundled sources for the artifact.
     *
     * @since 2.3
     */
    @Parameter(property = "sources")
    private Path sources;

    /**
     * Location of an existing POM file to be installed alongside the main artifact, given by the {@link #file}
     * parameter.
     *
     * @since 2.1
     */
    @Parameter(property = "pomFile")
    private Path pomFile;

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
    private Path localRepositoryPath;

    @Override
    public void execute() {
        if (!Files.exists(file)) {
            String message = "The specified file '" + file + "' does not exist";
            log.error(message);
            throw new MojoException(message);
        }

        Session session = this.session;

        List<Artifact> installableArtifacts = new ArrayList<>();

        // Override the default local repository
        if (localRepositoryPath != null) {
            session = session.withLocalRepository(session.createLocalRepository(localRepositoryPath));

            log.debug("localRepoPath: " + localRepositoryPath);
        }

        Path deployedPom;
        Path temporaryPom = null;
        if (pomFile != null) {
            deployedPom = pomFile;
            processModel(readModel(deployedPom));
        } else {
            if (!Boolean.TRUE.equals(generatePom)) {
                temporaryPom = readingPomFromJarFile();
                deployedPom = temporaryPom;
                if (deployedPom != null) {
                    log.debug("Using JAR embedded POM as pomFile");
                }
            } else {
                deployedPom = null;
            }
        }

        if (groupId == null || artifactId == null || version == null || packaging == null) {
            throw new MojoException("The artifact information is incomplete: 'groupId', 'artifactId', "
                    + "'version' and 'packaging' are required.");
        }

        if (!isValidId(groupId) || !isValidId(artifactId) || !isValidVersion(version)) {
            throw new MojoException("The artifact information is not valid: uses invalid characters.");
        }

        boolean isFilePom = classifier == null && "pom".equals(packaging);
        Artifact artifact = session.createArtifact(
                groupId, artifactId, version, classifier, isFilePom ? "pom" : getExtension(file), packaging);

        if (file.equals(getLocalRepositoryFile(artifact))) {
            throw new MojoException("Cannot install artifact. "
                    + "Artifact is already in the local repository.\n\nFile in question is: " + file + "\n");
        }

        ArtifactManager artifactManager = session.getService(ArtifactManager.class);
        artifactManager.setPath(artifact, file);
        installableArtifacts.add(artifact);

        if (!isFilePom) {
            Artifact pomArtifact = session.createArtifact(groupId, artifactId, version, null, "pom", null);
            if (deployedPom != null) {
                artifactManager.setPath(pomArtifact, deployedPom);
                installableArtifacts.add(pomArtifact);
            } else {
                temporaryPom = generatePomFile();
                deployedPom = temporaryPom;
                artifactManager.setPath(pomArtifact, deployedPom);
                if (Boolean.TRUE.equals(generatePom)
                        || (generatePom == null && !Files.exists(getLocalRepositoryFile(pomArtifact)))) {
                    log.debug("Installing generated POM");
                    installableArtifacts.add(pomArtifact);
                } else if (generatePom == null) {
                    log.debug("Skipping installation of generated POM, already present in local repository");
                }
            }
        }

        if (sources != null) {
            Artifact sourcesArtifact = session.createArtifact(groupId, artifactId, version, "sources", "jar", null);
            artifactManager.setPath(sourcesArtifact, sources);
            installableArtifacts.add(sourcesArtifact);
        }

        if (javadoc != null) {
            Artifact javadocArtifact = session.createArtifact(groupId, artifactId, version, "javadoc", "jar", null);
            artifactManager.setPath(javadocArtifact, javadoc);
            installableArtifacts.add(javadocArtifact);
        }

        try {
            ArtifactInstaller artifactInstaller = session.getService(ArtifactInstaller.class);
            artifactInstaller.install(session, installableArtifacts);
        } catch (ArtifactInstallerException e) {
            throw new MojoException(e.getMessage(), e);
        } finally {
            if (temporaryPom != null) {
                try {
                    Files.deleteIfExists(temporaryPom);
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private Path readingPomFromJarFile() {
        Pattern pomEntry = Pattern.compile("META-INF/maven/.*/pom\\.xml");
        try {
            try (JarFile jarFile = new JarFile(file.toFile())) {
                JarEntry entry = jarFile.stream()
                        .filter(e -> pomEntry.matcher(e.getName()).matches())
                        .findFirst()
                        .orElse(null);
                if (entry != null) {
                    log.debug("Loading " + entry.getName());

                    try (InputStream pomInputStream = jarFile.getInputStream(entry)) {
                        String base = file.getFileName().toString();
                        if (base.indexOf('.') > 0) {
                            base = base.substring(0, base.lastIndexOf('.'));
                        }
                        Path pomFile = File.createTempFile(base, ".pom").toPath();

                        Files.copy(pomInputStream, pomFile, StandardCopyOption.REPLACE_EXISTING);

                        processModel(readModel(pomFile));

                        return pomFile;
                    }
                } else {
                    log.info("pom.xml not found in " + file.getFileName());
                }
            }
        } catch (IOException e) {
            // ignore, artifact not packaged by Maven
        }
        return null;
    }

    /**
     * Parses a POM.
     *
     * @param pomFile The path of the POM file to parse, must not be <code>null</code>.
     * @return The model from the POM file, never <code>null</code>.
     * @throws MojoException If the POM could not be parsed.
     */
    private Model readModel(Path pomFile) throws MojoException {
        try {
            try (InputStream is = Files.newInputStream(pomFile)) {
                return session.getService(ModelXmlFactory.class).read(is);
            }
        } catch (FileNotFoundException e) {
            throw new MojoException("File not found " + pomFile, e);
        } catch (IOException e) {
            throw new MojoException("Error reading POM " + pomFile, e);
        } catch (XmlReaderException e) {
            throw new MojoException("Error parsing POM " + pomFile, e);
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
        return Model.newBuilder()
                .modelVersion("4.0.0")
                .groupId(groupId)
                .artifactId(artifactId)
                .version(version)
                .packaging(packaging)
                .description("POM was created from install:install-file")
                .build();
    }

    /**
     * Generates a (temporary) POM file from the plugin configuration. It's the responsibility of the caller to delete
     * the generated file when no longer needed.
     *
     * @return The path to the generated POM file, never <code>null</code>.
     * @throws MojoException If the POM file could not be generated.
     */
    private Path generatePomFile() throws MojoException {
        Model model = generateModel();
        try {
            Path pomFile = File.createTempFile("mvninstall", ".pom").toPath();
            try (Writer writer = Files.newBufferedWriter(pomFile)) {
                session.getService(ModelXmlFactory.class).write(model, writer);
            }
            return pomFile;
        } catch (IOException e) {
            throw new MojoException("Error writing temporary POM file: " + e.getMessage(), e);
        }
    }

    /**
     * Gets the path of the specified artifact within the local repository. Note that the returned path need not exist
     * (yet).
     */
    private Path getLocalRepositoryFile(Artifact artifact) {
        return session.getPathForLocalArtifact(artifact);
    }

    /**
     * Get file extension, honoring various {@code tar.xxx} combinations.
     */
    private String getExtension(final Path file) {
        String filename = file.getFileName().toString();
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0 && lastDot < filename.length() - 1) {
            String ext = filename.substring(lastDot + 1);
            return filename.regionMatches(lastDot + 1 - TAR.length(), TAR, 0, TAR.length()) ? TAR + ext : ext;
        }
        return "";
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

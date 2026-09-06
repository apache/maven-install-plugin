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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.ProducedArtifact;
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

    /** The {@code encoding} pseudo-attribute of an XML declaration. */
    private static final Pattern ENCODING_PSEUDO_ATTR = Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']");

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

        List<ProducedArtifact> installableArtifacts = new ArrayList<>();

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
            if (Boolean.TRUE.equals(generatePom)) {
                deployedPom = null;
            } else if (groupId != null && artifactId != null && version != null && packaging != null) {
                // The operator supplied the complete coordinates: do not let metadata embedded inside the
                // (potentially untrusted) file silently become the authoritative POM for those coordinates.
                // A minimal POM is generated below instead; use -DpomFile to install a curated POM.
                log.info("Ignoring any POM embedded in " + file.getFileName()
                        + ": complete coordinates were supplied, so a minimal POM will be generated instead."
                        + " Use -DpomFile to install a specific POM.");
                deployedPom = null;
            } else {
                // Coordinates that were supplied are cross-checked against the embedded POM inside
                // readingPomFromJarFile(): a mismatch fails the build rather than installing the
                // embedded POM verbatim at operator-chosen coordinates.
                temporaryPom = readingPomFromJarFile();
                deployedPom = temporaryPom;
                if (deployedPom != null) {
                    log.info("Using JAR embedded POM as pomFile");
                }
            }
        }

        if (groupId == null || artifactId == null || version == null || packaging == null) {
            throw new MojoException("The artifact information is incomplete: 'groupId', 'artifactId', "
                    + "'version' and 'packaging' are required.");
        }

        if (!isValidGroupId(groupId) || !isValidId(artifactId) || !isValidId(packaging) || !isValidVersion(version)) {
            throw new MojoException(
                    "The artifact information is not valid: uses invalid characters or empty/dot-only values.");
        }

        boolean isFilePom = classifier == null && "pom".equals(packaging);
        ProducedArtifact artifact = session.createProducedArtifact(
                groupId, artifactId, version, classifier, isFilePom ? "pom" : getExtension(file), packaging);

        Path localRepositoryFile = getLocalRepositoryFile(artifact);
        if (file.equals(localRepositoryFile)) {
            throw new MojoException("Cannot install artifact. "
                    + "Artifact is already in the local repository.\n\nFile in question is: " + file + "\n");
        }
        if (Files.exists(localRepositoryFile) && contentDiffers(localRepositoryFile, file)) {
            log.warn("The local repository already contains " + groupId + ":" + artifactId + ":" + version + " at "
                    + localRepositoryFile + " with different content: it will be overwritten by " + file);
        }

        // Defense in depth: however the coordinates were obtained, the composed layout path must stay
        // inside the local repository.
        Path repositoryRoot =
                session.getLocalRepository().getPath().toAbsolutePath().normalize();
        Path resolvedInstallPath =
                session.getPathForLocalArtifact(artifact).toAbsolutePath().normalize();
        if (!resolvedInstallPath.startsWith(repositoryRoot)) {
            throw new MojoException("The artifact coordinates " + groupId + ":" + artifactId + ":" + version
                    + " resolve to a path outside the local repository: " + resolvedInstallPath
                    + " is not under " + repositoryRoot);
        }

        ArtifactManager artifactManager = session.getService(ArtifactManager.class);
        artifactManager.setPath(artifact, file);
        installableArtifacts.add(artifact);

        ProducedArtifact pomArtifact = null;
        if (!isFilePom) {
            pomArtifact = session.createProducedArtifact(groupId, artifactId, version, null, "pom", null);
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
            ProducedArtifact sourcesArtifact =
                    session.createProducedArtifact(groupId, artifactId, version, "sources", "jar", null);
            artifactManager.setPath(sourcesArtifact, sources);
            installableArtifacts.add(sourcesArtifact);
        }

        if (javadoc != null) {
            ProducedArtifact javadocArtifact =
                    session.createProducedArtifact(groupId, artifactId, version, "javadoc", "jar", null);
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
                if (pomArtifact != null) {
                    artifactManager.setPath(pomArtifact, null);
                }
            }
        }
    }

    private Path readingPomFromJarFile() {
        Pattern pomEntry = Pattern.compile("META-INF/maven/.*/pom\\.xml");
        try {
            try (JarFile jarFile = new JarFile(file.toFile())) {
                List<JarEntry> pomEntries = jarFile.stream()
                        .filter(e -> pomEntry.matcher(e.getName()).matches())
                        .collect(Collectors.toList());
                if (pomEntries.size() > 1) {
                    throw new MojoException("Found " + pomEntries.size() + " POM entries in " + file.getFileName()
                            + ": "
                            + pomEntries.stream().map(JarEntry::getName).collect(Collectors.joining(", "))
                            + ". Cannot decide which one to trust: use -DpomFile or supply explicit"
                            + " -DgroupId/-DartifactId/-Dversion/-Dpackaging instead.");
                }
                JarEntry entry = pomEntries.isEmpty() ? null : pomEntries.get(0);
                if (entry != null) {
                    log.info("Loading " + entry.getName() + " from " + file.getFileName());

                    try (InputStream pomInputStream = jarFile.getInputStream(entry)) {
                        String base = file.getFileName().toString();
                        if (base.indexOf('.') > 0) {
                            base = base.substring(0, base.lastIndexOf('.'));
                        }
                        Path pomFile = File.createTempFile(base, ".pom").toPath();

                        Files.copy(pomInputStream, pomFile, StandardCopyOption.REPLACE_EXISTING);

                        rejectDoctype(pomFile, entry.getName());
                        Model model = readModel(pomFile);
                        validateEmbeddedPomEntryPath(entry.getName(), model);
                        crossCheckOperatorCoordinates(entry.getName(), model);
                        processModel(model);
                        log.info("Using coordinates " + groupId + ":" + artifactId + ":" + version + ":" + packaging
                                + " from JAR embedded POM " + entry.getName());

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
     * Defense-in-depth pre-screen for the JAR embedded POM: the entry is wire-origin, potentially
     * attacker-authored XML, and it is handed to the session's {@link ModelXmlFactory} whose DTD and
     * external-entity posture this plugin can neither configure nor guarantee across core versions.
     * A valid POM never carries a DOCTYPE declaration, so any embedded POM containing one is rejected
     * before it reaches the parser. The scan is encoding-aware: the bytes are decoded with the same
     * charset an XML parser is required to autodetect (BOM / first-bytes / encoding pseudo-attribute,
     * XML 1.0 Appendix F), so a UTF-16 or UTF-32 document cannot smuggle a NUL-interleaved DOCTYPE past
     * a naive single-byte scan. The match is case-sensitive because the XML spec only recognizes the
     * literal {@code <!DOCTYPE} keyword. This intentionally does not apply to the operator's own
     * {@code -DpomFile}.
     *
     * @param embeddedPom the temporary file holding the extracted entry, must not be <code>null</code>
     * @param entryName the name of the matched JAR entry, must not be <code>null</code>
     * @throws MojoException if the content contains a DOCTYPE declaration or cannot be read or decoded
     */
    private void rejectDoctype(Path embeddedPom, String entryName) throws MojoException {
        String content;
        try {
            byte[] bytes = Files.readAllBytes(embeddedPom);
            content = new String(bytes, detectXmlCharset(bytes, entryName));
        } catch (IOException e) {
            throw new MojoException("Error reading embedded POM " + embeddedPom, e);
        }
        if (content.contains("<!DOCTYPE")) {
            throw new MojoException("The POM embedded in " + file.getFileName() + " (" + entryName
                    + ") contains a DOCTYPE declaration. A POM must not declare a DOCTYPE; refusing to"
                    + " parse untrusted embedded XML with a DTD. Use -DpomFile or supply explicit"
                    + " coordinates instead.");
        }
    }

    /**
     * Detects the character encoding of raw XML bytes the way an XML parser is required to
     * (XML 1.0 Appendix F): byte-order mark first, then the byte pattern of a leading {@code <?xml},
     * then the {@code encoding} pseudo-attribute of the XML declaration, defaulting to UTF-8. Screening
     * in the encoding the parser will actually use is what makes the DOCTYPE screen sound. Fails closed
     * on encodings this JVM cannot decode and on an XML declaration that does not terminate within the
     * sniffed prefix: content that cannot be screened cannot be declared DOCTYPE-free.
     *
     * @param bytes the raw entry bytes, must not be <code>null</code>
     * @param entryName the name of the matched JAR entry, for error messages
     * @return the detected charset, never <code>null</code>
     * @throws MojoException if the detected/declared encoding is unsupported by this JVM
     */
    private Charset detectXmlCharset(byte[] bytes, String entryName) throws MojoException {
        if (bytes.length >= 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            int b2 = bytes[2] & 0xFF;
            int b3 = bytes[3] & 0xFF;
            // 4-byte BOMs and BOM-less "<?xml" patterns (must be checked before the 2-byte UTF-16 BOMs,
            // since the UTF-32LE BOM FF FE 00 00 starts with the UTF-16LE BOM FF FE)
            if (b0 == 0x00 && b1 == 0x00 && b2 == 0xFE && b3 == 0xFF) {
                return charsetOrFail("UTF-32BE", entryName);
            }
            if (b0 == 0xFF && b1 == 0xFE && b2 == 0x00 && b3 == 0x00) {
                return charsetOrFail("UTF-32LE", entryName);
            }
            if (b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x3C) {
                return charsetOrFail("UTF-32BE", entryName);
            }
            if (b0 == 0x3C && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
                return charsetOrFail("UTF-32LE", entryName);
            }
            if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x3F) {
                return StandardCharsets.UTF_16BE;
            }
            if (b0 == 0x3C && b1 == 0x00 && b2 == 0x3F && b3 == 0x00) {
                return StandardCharsets.UTF_16LE;
            }
            if (b0 == 0x4C && b1 == 0x6F && b2 == 0xA7 && b3 == 0x94) {
                // "<?xm" in EBCDIC. EBCDIC code pages disagree on variant bytes ('!' is 0x5A in IBM037
                // but 0x4F in IBM500), so screening in a guessed page would let a DOCTYPE through. The
                // XML declaration itself is recoverable with any EBCDIC page — letters, digits, '=',
                // '"' and '\'' are EBCDIC-invariant — so decode the prefix with IBM037, honor the
                // declared encoding, and fail closed when it is absent: EBCDIC XML has no default
                // encoding and must carry an encoding declaration (XML 1.0 section 4.3.3).
                String ebcdicPrefix =
                        new String(bytes, 0, Math.min(bytes.length, 1024), charsetOrFail("IBM037", entryName));
                int ebcdicDeclEnd = ebcdicPrefix.indexOf("?>");
                String ebcdicDecl = ebcdicDeclEnd >= 0 ? ebcdicPrefix.substring(0, ebcdicDeclEnd) : ebcdicPrefix;
                Matcher ebcdicMatcher = ENCODING_PSEUDO_ATTR.matcher(ebcdicDecl);
                if (ebcdicMatcher.find()) {
                    return charsetOrFail(ebcdicMatcher.group(1), entryName);
                }
                throw new MojoException("The POM embedded in " + file.getFileName() + " (" + entryName
                        + ") is EBCDIC-encoded but its XML declaration names no encoding, so the exact"
                        + " code page cannot be determined and it cannot be screened for a DOCTYPE"
                        + " declaration. Use -DpomFile or supply explicit coordinates instead.");
            }
        }
        if (bytes.length >= 2) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            if (b0 == 0xFE && b1 == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
            if (b0 == 0xFF && b1 == 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
        }
        // ASCII-compatible family (with or without a UTF-8 BOM): honor a declared encoding if the
        // document starts with an XML declaration, else the XML default of UTF-8 applies
        int offset =
                bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF
                        ? 3
                        : 0;
        String prefix = new String(bytes, offset, Math.min(bytes.length - offset, 1024), StandardCharsets.ISO_8859_1);
        if (prefix.startsWith("<?xml")) {
            int declEnd = prefix.indexOf("?>");
            if (declEnd < 0) {
                // The declaration does not close within the sniffed prefix. Declaration whitespace is
                // unbounded, so an encoding pseudo-attribute may legally sit past it (e.g. '<?xml
                // version="1.0"' + padding + 'encoding="IBM037"?>'); defaulting to UTF-8 here would
                // screen in the wrong charset while a declaration-honoring parser decodes the body in
                // the declared one. Fail closed, mirroring the EBCDIC branch.
                throw new MojoException("The POM embedded in " + file.getFileName() + " (" + entryName
                        + ") has an XML declaration that does not terminate within the sniffed prefix, so"
                        + " its declared encoding cannot be determined and it cannot be screened for a"
                        + " DOCTYPE declaration. Use -DpomFile or supply explicit coordinates instead.");
            }
            Matcher matcher = ENCODING_PSEUDO_ATTR.matcher(prefix.substring(0, declEnd));
            if (matcher.find()) {
                return charsetOrFail(matcher.group(1), entryName);
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * Resolves a detected or declared encoding name, failing closed if this JVM cannot decode it.
     */
    private Charset charsetOrFail(String name, String entryName) throws MojoException {
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) { // IllegalCharsetNameException, UnsupportedCharsetException
            throw new MojoException("The POM embedded in " + file.getFileName() + " (" + entryName
                    + ") uses an encoding this JVM cannot decode ('" + name + "'), so it cannot be screened"
                    + " for a DOCTYPE declaration. Use -DpomFile or supply explicit coordinates instead.");
        }
    }

    /**
     * Verifies that the {@code <groupId>/<artifactId>} components of the matched
     * {@code META-INF/maven/<groupId>/<artifactId>/pom.xml} entry path agree with the effective coordinates
     * declared by the embedded POM itself. A mismatch means the archive self-reports two different identities,
     * so its metadata cannot be trusted to choose the install coordinates.
     *
     * @param entryName the name of the matched JAR entry, must not be <code>null</code>
     * @param model the model parsed from that entry, must not be <code>null</code>
     * @throws MojoException if the entry path is not of the expected shape or does not match the model
     */
    private void validateEmbeddedPomEntryPath(String entryName, Model model) throws MojoException {
        String[] segments = entryName.split("/");
        // expected shape: META-INF/maven/<groupId>/<artifactId>/pom.xml
        if (segments.length != 5) {
            throw new MojoException("Unexpected embedded POM entry path '" + entryName + "' in "
                    + file.getFileName() + ": expected META-INF/maven/<groupId>/<artifactId>/pom.xml."
                    + " Use -DpomFile or supply explicit coordinates instead.");
        }
        String entryGroupId = segments[2];
        String entryArtifactId = segments[3];
        Parent parent = model.getParent();
        String modelGroupId =
                model.getGroupId() != null ? model.getGroupId() : (parent != null ? parent.getGroupId() : null);
        String modelArtifactId = model.getArtifactId();
        if (!entryGroupId.equals(modelGroupId) || !entryArtifactId.equals(modelArtifactId)) {
            throw new MojoException("The POM embedded in " + file.getFileName() + " declares " + modelGroupId + ":"
                    + modelArtifactId + " but is packaged under entry path '" + entryName + "' (" + entryGroupId
                    + ":" + entryArtifactId + "). Refusing to adopt coordinates from inconsistent embedded"
                    + " metadata: use -DpomFile or supply explicit -DgroupId/-DartifactId/-Dversion/-Dpackaging.");
        }
    }

    /**
     * Cross-checks every operator-supplied coordinate (groupId, artifactId, version) against the effective
     * values declared by the (potentially untrusted) embedded POM and fails on any mismatch. Operator values
     * may override embedded metadata only through the generated-POM path (all four coordinates supplied, or
     * {@code -DgeneratePom=true}) or through an explicit {@code -DpomFile}: on this path the embedded POM
     * itself is what gets installed at the final coordinates, so letting a partially-specified command line
     * (e.g. groupId/artifactId/version without packaging) coexist with a foreign embedded POM would install
     * an attacker-authored POM verbatim at operator-chosen coordinates.
     *
     * @param entryName the name of the matched JAR entry, must not be <code>null</code>
     * @param model the model parsed from that entry, must not be <code>null</code>
     * @throws MojoException if an operator-supplied coordinate disagrees with the embedded POM
     */
    private void crossCheckOperatorCoordinates(String entryName, Model model) throws MojoException {
        Parent parent = model.getParent();
        String modelGroupId =
                model.getGroupId() != null ? model.getGroupId() : (parent != null ? parent.getGroupId() : null);
        String modelArtifactId = model.getArtifactId();
        String modelVersion =
                model.getVersion() != null ? model.getVersion() : (parent != null ? parent.getVersion() : null);
        List<String> mismatches = new ArrayList<>();
        if (groupId != null && !groupId.equals(modelGroupId)) {
            mismatches.add("groupId: supplied '" + groupId + "' but embedded POM declares '" + modelGroupId + "'");
        }
        if (artifactId != null && !artifactId.equals(modelArtifactId)) {
            mismatches.add(
                    "artifactId: supplied '" + artifactId + "' but embedded POM declares '" + modelArtifactId + "'");
        }
        if (version != null && !version.equals(modelVersion)) {
            mismatches.add("version: supplied '" + version + "' but embedded POM declares '" + modelVersion + "'");
        }
        if (!mismatches.isEmpty()) {
            throw new MojoException("The POM embedded in " + file.getFileName() + " (" + entryName
                    + ") does not match the supplied coordinates: " + String.join("; ", mismatches)
                    + ". Refusing to install a mismatching embedded POM at operator-chosen coordinates:"
                    + " use -DpomFile, supply all of -DgroupId/-DartifactId/-Dversion/-Dpackaging, or pass"
                    + " -DgeneratePom=true.");
        }
    }

    /**
     * Returns {@code true} if the two files exist with different content, or cannot be compared
     * (fail-closed for warning purposes).
     */
    private boolean contentDiffers(Path existing, Path candidate) {
        try {
            return Files.mismatch(existing, candidate) != -1;
        } catch (IOException e) {
            return true;
        }
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
     * Returns {@code true} if passed in string is "valid Maven ID" (artifactId or packaging): non-empty,
     * not consisting solely of {@code '.'} characters (so it can never form a {@code .}/{@code ..} path
     * segment in the local repository layout), and using only allowed characters.
     */
    private boolean isValidId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        boolean seenNonDot = false;
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
            if (c != '.') {
                seenNonDot = true;
            }
        }
        return seenNonDot;
    }

    /**
     * Returns {@code true} if passed in string is a valid Maven groupId: a valid ID whose dot-separated
     * segments are all non-empty. Leading, trailing or consecutive dots would produce empty (or, on some
     * local-repository managers, absolute) path segments after the dots-to-directories transform of the
     * local repository layout, escaping the coordinate's directory.
     */
    private boolean isValidGroupId(String groupId) {
        return isValidId(groupId) && !groupId.startsWith(".") && !groupId.endsWith(".") && !groupId.contains("..");
    }

    /**
     * Returns {@code true} if passed in string is "valid Maven (simple. non range, expression, etc) version":
     * non-empty, not consisting solely of {@code '.'} characters (so it can never form a {@code .}/{@code ..}
     * path segment in the local repository layout), and free of illegal characters.
     */
    private boolean isValidVersion(String version) {
        if (version == null || version.isEmpty()) {
            return false;
        }
        boolean seenNonDot = false;
        for (int i = version.length() - 1; i >= 0; i--) {
            char c = version.charAt(i);
            if (ILLEGAL_VERSION_CHARS.indexOf(c) >= 0) {
                return false;
            }
            if (c != '.') {
                seenNonDot = true;
            }
        }
        return seenNonDot;
    }
}

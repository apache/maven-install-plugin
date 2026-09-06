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

import javax.inject.Inject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Objects.isNull;

/**
 * Installs a file in the local repository.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
@Mojo(name = "install-file", requiresProject = false, aggregator = true, threadSafe = true)
public class InstallFileMojo extends AbstractMojo {
    private static final String LS = System.lineSeparator();
    private static final String ILLEGAL_VERSION_CHARS = "\\/:\"<>|?*[](){},";

    /** The {@code encoding} pseudo-attribute of an XML declaration. */
    private static final Pattern ENCODING_PSEUDO_ATTR = Pattern.compile("encoding\\s*=\\s*[\"']([^\"']+)[\"']");

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final RepositorySystem repositorySystem;

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
     * Extension of the artifact to be installed. If set, will override plugin own logic to detect extension. If not set,
     * as Maven expected, packaging determines the artifact extension.
     *
     * @since 3.1.3
     */
    @Parameter(property = "extension")
    private String extension;

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

    private static final Predicate<String> IS_EMPTY = s -> isNull(s) || s.isEmpty();

    private static final Predicate<String> IS_POM_PACKAGING = "pom"::equals;

    @Inject
    public InstallFileMojo(RepositorySystem repositorySystem) {
        this.repositorySystem = repositorySystem;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (!file.exists()) {
            String message = "The specified file '" + file.getPath() + "' does not exist";
            log.error(message);
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
            log.debug(
                    "localRepoPath: {}", localRepositoryManager.getRepository().getBasedir());
        }

        File temporaryPom = null;

        if (pomFile != null) {
            processModel(readModel(pomFile));
        } else {
            if (Boolean.TRUE.equals(generatePom)) {
                // generatePom explicitly requested: skip embedded POM
            } else if (groupId != null && artifactId != null && version != null && packaging != null) {
                // The operator supplied the complete coordinates: do not let metadata embedded inside the
                // (potentially untrusted) file silently become the authoritative POM for those coordinates.
                // A minimal POM is generated below instead; use -DpomFile to install a curated POM.
                log.info(
                        "Ignoring any POM embedded in {}: complete coordinates were supplied, so a minimal"
                                + " POM will be generated instead. Use -DpomFile to install a specific POM.",
                        file.getName());
            } else {
                // Coordinates that were supplied are cross-checked against the embedded POM inside
                // readingPomFromJarFile(): a mismatch fails the build rather than installing the
                // embedded POM verbatim at operator-chosen coordinates.
                temporaryPom = readingPomFromJarFile();
                pomFile = temporaryPom;
                if (pomFile != null) {
                    log.info("Using JAR embedded POM as pomFile");
                }
            }
        }

        if (isNull(groupId) || isNull(artifactId) || isNull(version) || isNull(packaging)) {
            throw new MojoExecutionException("The artifact information is incomplete: 'groupId', 'artifactId', "
                    + "'version' and 'packaging' are required.");
        }

        if (!isValidGroupId(groupId) || !isValidId(artifactId) || !isValidId(packaging) || !isValidVersion(version)) {
            throw new MojoExecutionException(
                    "The artifact information is not valid: uses invalid characters or empty/dot-only values.");
        }

        InstallRequest installRequest = new InstallRequest();

        String mainArtifactExtension;
        if (classifier == null && "pom".equals(packaging)) {
            mainArtifactExtension = "pom";
        } else {
            ArtifactType artifactType =
                    session.getRepositorySession().getArtifactTypeRegistry().get(packaging);
            if (artifactType != null) {
                if (StringUtils.isEmpty(classifier) && !StringUtils.isEmpty(artifactType.getClassifier())) {
                    classifier = artifactType.getClassifier();
                }
                mainArtifactExtension = artifactType.getExtension();
            } else {
                mainArtifactExtension = packaging;
            }
        }
        if (extension != null && !Objects.equals(extension, mainArtifactExtension)) {
            log.warn(
                    "Main artifact extension should be '{}' but was overridden to '{}'",
                    mainArtifactExtension,
                    extension);
        }
        Artifact mainArtifact = new DefaultArtifact(
                        groupId, artifactId, classifier, extension != null ? extension : mainArtifactExtension, version)
                .setFile(file);
        installRequest.addArtifact(mainArtifact);

        File artifactLocalFile = getLocalRepositoryFile(repositorySystemSession, mainArtifact);
        File pomLocalFile = getPomLocalRepositoryFile(repositorySystemSession, mainArtifact);

        if (file.equals(artifactLocalFile)) {
            throw new MojoFailureException("Cannot install artifact. " + "Artifact is already in the local repository."
                    + LS + LS + "File in question is: " + file + LS);
        }
        if (artifactLocalFile.exists() && contentDiffers(artifactLocalFile, file)) {
            log.warn(
                    "The local repository already contains {}:{}:{} at {} with different content:"
                            + " it will be overwritten by {}",
                    groupId,
                    artifactId,
                    version,
                    artifactLocalFile,
                    file);
        }
        verifyContainment(repositorySystemSession, artifactLocalFile);

        if (!IS_POM_PACKAGING.test(packaging)) {
            if (isNull(pomFile)) {
                if (Boolean.TRUE.equals(generatePom) || (generatePom == null && !pomLocalFile.exists())) {
                    temporaryPom = generatePomFile();
                    log.debug("Installing generated POM");
                    installRequest.addArtifact(new SubArtifact(mainArtifact, "", "pom", temporaryPom));
                } else if (generatePom == null) {
                    log.debug("Skipping installation of generated POM, already present in local repository");
                }
            } else {
                installRequest.addArtifact(new SubArtifact(mainArtifact, "", "pom", pomFile));
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

    private static final Pattern POM_ENTRY_PATTERN = Pattern.compile("META-INF/maven/.*/pom\\.xml");

    private static final Predicate<JarEntry> IS_POM_ENTRY =
            entry -> POM_ENTRY_PATTERN.matcher(entry.getName()).matches();

    private File readingPomFromJarFile() throws MojoExecutionException {

        String base = file.getName();
        if (base.contains(".")) {
            base = base.substring(0, base.lastIndexOf('.'));
        }

        try (JarFile jarFile = new JarFile(file)) {

            List<JarEntry> pomEntries = jarFile.stream().filter(IS_POM_ENTRY).collect(Collectors.toList());
            if (pomEntries.size() > 1) {
                throw new MojoExecutionException("Found " + pomEntries.size() + " POM entries in " + file.getName()
                        + ": "
                        + pomEntries.stream().map(JarEntry::getName).collect(Collectors.joining(", "))
                        + ". Cannot decide which one to trust: use -DpomFile or supply explicit"
                        + " -DgroupId/-DartifactId/-Dversion/-Dpackaging instead.");
            }
            JarEntry pomEntry = pomEntries.isEmpty() ? null : pomEntries.get(0);

            if (isNull(pomEntry)) {
                // This means there is no entry which matches the "pom.xml"...(or in other words: not packaged by Maven)
                log.info("pom.xml not found in {}", file.getName());
                return null;
            }

            log.info("Loading {} from {}", pomEntry.getName(), file.getName());

            Path tempPomFile = Files.createTempFile(base, ".pom");

            Files.copy(jarFile.getInputStream(pomEntry), tempPomFile, StandardCopyOption.REPLACE_EXISTING);

            rejectDoctype(tempPomFile, pomEntry.getName());
            Model model = readModel(tempPomFile.toFile());
            validateEmbeddedPomEntryPath(pomEntry.getName(), model);
            crossCheckOperatorCoordinates(pomEntry.getName(), model);
            processModel(model);
            log.info(
                    "Using coordinates {}:{}:{}:{} from JAR embedded POM {}",
                    groupId,
                    artifactId,
                    version,
                    packaging,
                    pomEntry.getName());
            return tempPomFile.toFile();

        } catch (IOException e) {
            // ignore, artifact not packaged by Maven
            return null;
        }
    }

    /**
     * Parses a POM.
     *
     * @param pomFile The path of the POM file to parse, must not be <code>null</code>.
     * @return The model from the POM file, never <code>null</code>.
     * @throws MojoExecutionException If the POM could not be parsed.
     */
    private Model readModel(File pomFile) throws MojoExecutionException {
        try (InputStream reader = Files.newInputStream(pomFile.toPath())) {
            return new MavenXpp3Reader().read(reader);
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("File not found " + pomFile, e);
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading POM " + pomFile, e);
        } catch (XmlPullParserException e) {
            throw new MojoExecutionException("Error parsing POM " + pomFile, e);
        }
    }

    /**
     * Defense-in-depth pre-screen for the JAR embedded POM: the entry is wire-origin, potentially
     * attacker-authored XML, and it is handed to {@link MavenXpp3Reader} whose DTD and external-entity
     * posture this plugin can neither configure nor guarantee across core versions. A valid POM never
     * carries a DOCTYPE declaration, so any embedded POM containing one is rejected before it reaches
     * the parser. The scan is encoding-aware: the bytes are decoded with the same charset an XML parser
     * is required to autodetect (BOM / first-bytes / encoding pseudo-attribute, XML 1.0 Appendix F),
     * so a UTF-16 or UTF-32 document cannot smuggle a NUL-interleaved DOCTYPE past a naive single-byte
     * scan. This intentionally does not apply to the operator's own {@code -DpomFile}.
     *
     * @param embeddedPom the temporary file holding the extracted entry, must not be <code>null</code>
     * @param entryName the name of the matched JAR entry, must not be <code>null</code>
     * @throws MojoExecutionException if the content contains a DOCTYPE declaration or cannot be read
     */
    private void rejectDoctype(Path embeddedPom, String entryName) throws MojoExecutionException {
        String content;
        try {
            byte[] bytes = Files.readAllBytes(embeddedPom);
            content = new String(bytes, detectXmlCharset(bytes, entryName));
        } catch (IOException e) {
            throw new MojoExecutionException("Error reading embedded POM " + embeddedPom, e);
        }
        if (content.contains("<!DOCTYPE")) {
            throw new MojoExecutionException("The POM embedded in " + file.getName() + " (" + entryName
                    + ") contains a DOCTYPE declaration. A POM must not declare a DOCTYPE; refusing to"
                    + " parse untrusted embedded XML with a DTD. Use -DpomFile or supply explicit"
                    + " coordinates instead.");
        }
    }

    /**
     * Detects the character encoding of raw XML bytes the way an XML parser is required to
     * (XML 1.0 Appendix F): byte-order mark first, then the byte pattern of a leading {@code <?xml},
     * then the {@code encoding} pseudo-attribute of the XML declaration, defaulting to UTF-8.
     *
     * @param bytes the raw entry bytes, must not be <code>null</code>
     * @param entryName the name of the matched JAR entry, for error messages
     * @return the detected charset, never <code>null</code>
     * @throws MojoExecutionException if the detected/declared encoding is unsupported
     */
    private Charset detectXmlCharset(byte[] bytes, String entryName) throws MojoExecutionException {
        if (bytes.length >= 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;
            int b2 = bytes[2] & 0xFF;
            int b3 = bytes[3] & 0xFF;
            // 4-byte BOMs and BOM-less "<?xml" patterns
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
                // "<?xm" in EBCDIC
                String ebcdicPrefix =
                        new String(bytes, 0, Math.min(bytes.length, 1024), charsetOrFail("IBM037", entryName));
                int ebcdicDeclEnd = ebcdicPrefix.indexOf("?>");
                String ebcdicDecl = ebcdicDeclEnd >= 0 ? ebcdicPrefix.substring(0, ebcdicDeclEnd) : ebcdicPrefix;
                Matcher ebcdicMatcher = ENCODING_PSEUDO_ATTR.matcher(ebcdicDecl);
                if (ebcdicMatcher.find()) {
                    return charsetOrFail(ebcdicMatcher.group(1), entryName);
                }
                throw new MojoExecutionException("The POM embedded in " + file.getName() + " (" + entryName
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
        // ASCII-compatible family: honor a declared encoding if present
        int offset =
                bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF
                        ? 3
                        : 0;
        String prefix = new String(bytes, offset, Math.min(bytes.length - offset, 1024), StandardCharsets.ISO_8859_1);
        if (prefix.startsWith("<?xml")) {
            int declEnd = prefix.indexOf("?>");
            if (declEnd < 0) {
                throw new MojoExecutionException("The POM embedded in " + file.getName() + " (" + entryName
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
    private Charset charsetOrFail(String name, String entryName) throws MojoExecutionException {
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException e) {
            throw new MojoExecutionException("The POM embedded in " + file.getName() + " (" + entryName
                    + ") uses an encoding this JVM cannot decode ('" + name + "'), so it cannot be screened"
                    + " for a DOCTYPE declaration. Use -DpomFile or supply explicit coordinates instead.");
        }
    }

    /**
     * Verifies that the {@code <groupId>/<artifactId>} components of the matched
     * {@code META-INF/maven/<groupId>/<artifactId>/pom.xml} entry path agree with the effective
     * coordinates declared by the embedded POM itself.
     *
     * @param entryName the name of the matched JAR entry, must not be <code>null</code>
     * @param model the model parsed from that entry, must not be <code>null</code>
     * @throws MojoExecutionException if the entry path does not match the model
     */
    private void validateEmbeddedPomEntryPath(String entryName, Model model) throws MojoExecutionException {
        String[] segments = entryName.split("/");
        // expected shape: META-INF/maven/<groupId>/<artifactId>/pom.xml
        if (segments.length != 5) {
            throw new MojoExecutionException("Unexpected embedded POM entry path '" + entryName + "' in "
                    + file.getName() + ": expected META-INF/maven/<groupId>/<artifactId>/pom.xml."
                    + " Use -DpomFile or supply explicit coordinates instead.");
        }
        String entryGroupId = segments[2];
        String entryArtifactId = segments[3];
        Parent parent = model.getParent();
        String modelGroupId =
                model.getGroupId() != null ? model.getGroupId() : (parent != null ? parent.getGroupId() : null);
        String modelArtifactId = model.getArtifactId();
        if (!entryGroupId.equals(modelGroupId) || !entryArtifactId.equals(modelArtifactId)) {
            throw new MojoExecutionException("The POM embedded in " + file.getName() + " declares " + modelGroupId
                    + ":" + modelArtifactId + " but is packaged under entry path '" + entryName + "' ("
                    + entryGroupId + ":" + entryArtifactId + "). Refusing to adopt coordinates from inconsistent"
                    + " embedded metadata: use -DpomFile or supply explicit"
                    + " -DgroupId/-DartifactId/-Dversion/-Dpackaging.");
        }
    }

    /**
     * Cross-checks every operator-supplied coordinate (groupId, artifactId, version) against the
     * effective values declared by the embedded POM and fails on any mismatch.
     *
     * @param entryName the name of the matched JAR entry, must not be <code>null</code>
     * @param model the model parsed from that entry, must not be <code>null</code>
     * @throws MojoExecutionException if an operator-supplied coordinate disagrees with the embedded POM
     */
    private void crossCheckOperatorCoordinates(String entryName, Model model) throws MojoExecutionException {
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
            throw new MojoExecutionException("The POM embedded in " + file.getName() + " (" + entryName
                    + ") does not match the supplied coordinates: " + String.join("; ", mismatches)
                    + ". Refusing to install a mismatching embedded POM at operator-chosen coordinates:"
                    + " use -DpomFile, supply all of -DgroupId/-DartifactId/-Dversion/-Dpackaging, or pass"
                    + " -DgeneratePom=true.");
        }
    }

    /**
     * Returns {@code true} if the two files exist with different content, or cannot be compared
     * (fail-closed for warning purposes). Uses byte-by-byte comparison since {@code Files.mismatch()}
     * is not available on Java 8.
     */
    private boolean contentDiffers(File existing, File candidate) {
        try {
            if (existing.length() != candidate.length()) {
                return true;
            }
            byte[] existingBytes = Files.readAllBytes(existing.toPath());
            byte[] candidateBytes = Files.readAllBytes(candidate.toPath());
            for (int i = 0; i < existingBytes.length; i++) {
                if (existingBytes[i] != candidateBytes[i]) {
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Defense in depth: verifies that the composed layout path stays inside the local repository.
     */
    private void verifyContainment(RepositorySystemSession repoSession, File artifactLocalFile)
            throws MojoExecutionException {
        File repositoryRoot = repoSession.getLocalRepository().getBasedir().getAbsoluteFile();
        File resolvedInstallPath = artifactLocalFile.getAbsoluteFile();
        try {
            String repoCanonical = repositoryRoot.getCanonicalPath();
            String installCanonical = resolvedInstallPath.getCanonicalPath();
            if (!installCanonical.startsWith(repoCanonical + File.separator)
                    && !installCanonical.equals(repoCanonical)) {
                throw new MojoExecutionException("The artifact coordinates " + groupId + ":" + artifactId + ":"
                        + version + " resolve to a path outside the local repository: " + resolvedInstallPath
                        + " is not under " + repositoryRoot);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to resolve canonical path for containment check", e);
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
        try {
            Path tempPomFile = Files.createTempFile("mvninstall", ".pom");

            try (OutputStream writer = Files.newOutputStream(tempPomFile)) {
                new MavenXpp3Writer().write(writer, model);
                return tempPomFile.toFile();
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Error writing temporary POM file: " + e.getMessage(), e);
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
     * segments are all non-empty. Leading, trailing or consecutive dots would produce empty path segments
     * after the dots-to-directories transform of the local repository layout.
     */
    private boolean isValidGroupId(String groupId) {
        return isValidId(groupId) && !groupId.startsWith(".") && !groupId.endsWith(".") && !groupId.contains("..");
    }

    /**
     * Returns {@code true} if passed in string is "valid Maven (simple. non range, expression, etc) version":
     * non-empty, not consisting solely of {@code '.'} characters, and free of illegal characters.
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

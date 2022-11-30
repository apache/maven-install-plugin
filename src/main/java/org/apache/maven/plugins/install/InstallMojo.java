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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.MojoExecution;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactInstaller;
import org.apache.maven.api.services.ArtifactInstallerRequest;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;

/**
 * Installs the project's main artifact, and any other artifacts attached by other plugins in the lifecycle, to the
 * local repository.
 */
@SuppressWarnings("unused")
@Mojo(name = "install", defaultPhase = LifecyclePhase.INSTALL)
public class InstallMojo implements org.apache.maven.api.plugin.Mojo {
    @Component
    private Log log;

    @Component
    private Session session;

    @Component
    private Project project;

    @Component
    private MojoExecution mojoExecution;

    /**
     * Whether every project should be installed during its own install-phase or at the end of the multimodule build. If
     * set to {@code true} and the build fails, none of the reactor projects is installed.
     * <strong>(experimental)</strong>
     *
     * @since 2.5
     */
    @Parameter(property = "installAtEnd", defaultValue = "false")
    private boolean installAtEnd;

    /**
     * Set this to <code>true</code> to bypass artifact installation. Use this for artifacts that do not need to be
     * installed in the local repository.
     *
     * @since 2.4
     */
    @Parameter(property = "maven.install.skip", defaultValue = "false")
    private boolean skip;

    private enum State {
        SKIPPED,
        INSTALLED,
        TO_BE_INSTALLED
    }

    private static final String INSTALL_PROCESSED_MARKER = InstallMojo.class.getName() + ".processed";

    private void putState(State state) {
        session.getPluginContext(project).put(INSTALL_PROCESSED_MARKER, state.name());
    }

    private void putState(State state, ArtifactInstallerRequest request) {
        session.getPluginContext(project).put(INSTALL_PROCESSED_MARKER, state.name());
        session.getPluginContext(project).put(ArtifactInstallerRequest.class.getName(), request);
    }

    private State getState(Project project) {
        Map<String, Object> pluginContext = session.getPluginContext(project);
        return State.valueOf((String) pluginContext.get(INSTALL_PROCESSED_MARKER));
    }

    private boolean hasState(Project project) {
        Map<String, Object> pluginContext = session.getPluginContext(project);
        return pluginContext.containsKey(INSTALL_PROCESSED_MARKER);
    }

    private boolean usingPlugin(Project project) {
        Plugin plugin = project.getBuild().getPluginsAsMap().get("org.apache.maven.plugins:maven-install-plugin");
        return plugin != null
                && plugin.getExecutions().stream()
                        .anyMatch(e -> Objects.equals(e.getId(), mojoExecution.getExecutionId())
                                && !"none".equals(e.getPhase()));
    }

    @Override
    public void execute() {
        if (skip) {
            log.info("Skipping artifact installation");
            putState(State.SKIPPED);
        } else {
            if (!installAtEnd) {
                installProject(processProject(project));
                putState(State.INSTALLED);
            } else {
                log.info("Deferring install for " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                        + project.getVersion() + " at end");
                putState(State.TO_BE_INSTALLED, processProject(project));
            }
        }

        List<Project> projectsUsingPlugin =
                session.getProjects().stream().filter(this::usingPlugin).collect(Collectors.toList());
        if (allProjectsMarked(projectsUsingPlugin)) {
            for (Project reactorProject : projectsUsingPlugin) {
                State state = getState(reactorProject);
                if (state == State.TO_BE_INSTALLED) {
                    Map<String, Object> pluginContext = session.getPluginContext(reactorProject);
                    ArtifactInstallerRequest request =
                            (ArtifactInstallerRequest) pluginContext.get(ArtifactInstallerRequest.class.getName());
                    installProject(request);
                }
            }
        }
    }

    private boolean allProjectsMarked(List<Project> projectsUsingPlugin) {
        return projectsUsingPlugin.stream().allMatch(this::hasState);
    }

    private void installProject(ArtifactInstallerRequest request) {
        try {
            ArtifactInstaller artifactInstaller = session.getService(ArtifactInstaller.class);
            artifactInstaller.install(request);
        } catch (MojoException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoException(e.getMessage(), e);
        }
    }

    /**
     * Processes passed in {@link Project} and produces {@link ArtifactInstallerRequest} out of it.
     *
     * @throws IllegalArgumentException if project is badly set up.
     */
    private ArtifactInstallerRequest processProject(Project project) {
        ArtifactManager artifactManager = session.getService(ArtifactManager.class);
        ProjectManager projectManager = session.getService(ProjectManager.class);
        Predicate<Artifact> isValidPath =
                a -> artifactManager.getPath(a).filter(Files::isRegularFile).isPresent();

        Artifact artifact = project.getArtifact();
        Collection<Artifact> attachedArtifacts = projectManager.getAttachedArtifacts(project);
        Path pomPath = project.getPomPath().orElse(null);

        List<Artifact> installables = new ArrayList<>();

        if (!"pom".equals(project.getPackaging())) {
            // pom
            Artifact pomArtifact =
                    session.createArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion(), "pom");
            artifactManager.setPath(pomArtifact, pomPath);
            installables.add(pomArtifact);
            // main artifact
            if (!isValidPath.test(artifact) && !attachedArtifacts.isEmpty()) {
                throw new MojoException("The packaging plugin for this project did not assign "
                        + "a main file to the project but it has attachments. Change packaging to 'pom'.");
            }
            installables.add(artifact);
        } else {
            artifactManager.setPath(artifact, pomPath);
            installables.add(artifact);
        }

        installables.addAll(attachedArtifacts);
        for (Artifact installable : installables) {
            if (!isValidPath.test(installable)) {
                throw new MojoException("The packaging for this project did not assign "
                        + "a file to the attached artifact: " + artifact);
            }
        }

        return ArtifactInstallerRequest.builder()
                .session(session)
                .artifacts(installables)
                .build();
    }

    void setSkip(boolean skip) {
        this.skip = skip;
    }
}

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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
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
@Mojo(name = "install", defaultPhase = "install")
public class InstallMojo implements org.apache.maven.api.plugin.Mojo {
    @Inject
    private Log log;

    @Inject
    private Session session;

    @Inject
    private Project project;

    /**
     * Whether every project should be installed during its own install-phase or at the end of the multimodule build. If
     * set to {@code true} and the build fails before the deferred installation has started, none of the reactor
     * projects is installed. If a failure occurs during the deferred installation itself, an explicit inventory of the
     * projects already installed and those skipped is logged.
     * <strong>(experimental)</strong>
     *
     * @since 2.5
     */
    @Parameter(property = "installAtEnd", defaultValue = "true")
    private boolean installAtEnd;

    /**
     * Set this to <code>true</code> to bypass artifact installation. Use this for artifacts that do not need to be
     * installed in the local repository.
     *
     * @since 2.4
     */
    @Parameter(property = "maven.install.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Set this to <code>true</code> to allow incomplete project processing. By default, such projects are forbidden
     * and Mojo will fail to process them. Incomplete project is a Maven Project that has any other packaging than
     * "pom" and has no main artifact packaged. In the majority of cases, what user really wants here is a project
     * with "pom" packaging and some classified artifact attached (typical example is some assembly being packaged
     * and attached with classifier).
     *
     * @since 3.1.1
     */
    @Parameter(property = "allowIncompleteProjects", defaultValue = "false")
    private boolean allowIncompleteProjects;

    private enum State {
        SKIPPED,
        INSTALLED,
        TO_BE_INSTALLED
    }

    private static final String INSTALL_PROCESSED_MARKER = InstallMojo.class.getName() + ".processed";
    private static final String PROJECTS_USING_PLUGIN_KEY = InstallMojo.class.getName() + ".projectsUsingPlugin";

    /**
     * Guards the mark-check-fire sequence of the deferred ({@code installAtEnd}) install: without it, two
     * modules finishing simultaneously in a parallel build ({@code -T}) can both observe "all projects
     * marked" and each run the full deferred-install loop. The plugin classloader (and therefore this lock)
     * is shared across all reactor threads of a build.
     */
    private static final Object DEFERRED_INSTALL_LOCK = new Object();

    public InstallMojo() {}

    private void putState(State state) {
        session.getPluginContext(project).put(INSTALL_PROCESSED_MARKER, state.name());
    }

    private void putState(State state, ArtifactInstallerRequest request) {
        session.getPluginContext(project).put(INSTALL_PROCESSED_MARKER, state.name());
        session.getPluginContext(project).put(ArtifactInstallerRequest.class.getName(), request);
    }

    private void putState(Project project, State state) {
        session.getPluginContext(project).put(INSTALL_PROCESSED_MARKER, state.name());
    }

    private State getState(Project project) {
        Map<String, Object> pluginContext = session.getPluginContext(project);
        return State.valueOf((String) pluginContext.get(INSTALL_PROCESSED_MARKER));
    }

    private boolean hasState(Project project) {
        Map<String, Object> pluginContext = session.getPluginContext(project);
        return pluginContext.containsKey(INSTALL_PROCESSED_MARKER);
    }

    /**
     * Returns the list of reactor projects that have this plugin configured, cached on first call.
     * The list is invariant during a build and is stored in the current project's plugin
     * context to avoid recomputing it on every module invocation (O(N) total instead of O(N²)).
     */
    @SuppressWarnings("unchecked")
    private List<Project> getProjectsUsingPlugin() {
        List<Project> allProjects = session.getProjects();
        if (allProjects.isEmpty()) {
            return List.of();
        }
        Map<String, Object> ctx = session.getPluginContext(allProjects.get(0));
        return (List<Project>) ctx.computeIfAbsent(
                PROJECTS_USING_PLUGIN_KEY,
                k -> allProjects.stream().filter(this::usingPlugin).collect(Collectors.toList()));
    }

    /**
     * Whether the given reactor project takes part in the deferred install. Grouping is by plugin presence
     * (any execution not bound to phase {@code none}), not by execution-id equality: matching only the current
     * execution's id would let a module binding the goal under a custom id observe a singleton project set,
     * trivially satisfy {@link #allProjectsMarked(List)}, and install mid-build, breaking the all-or-nothing
     * contract documented on {@link #installAtEnd}.
     */
    private boolean usingPlugin(Project project) {
        Plugin plugin = project.getBuild().getPluginsAsMap().get("org.apache.maven.plugins:maven-install-plugin");
        return plugin != null && plugin.getExecutions().stream().anyMatch(e -> !"none".equals(e.getPhase()));
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
                getLog().info("Deferring install for " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                        + project.getVersion() + " at end");
                putState(State.TO_BE_INSTALLED, processProject(project));
            }
        }

        List<Project> projectsUsingPlugin = getProjectsUsingPlugin();
        synchronized (DEFERRED_INSTALL_LOCK) {
            if (allProjectsMarked(projectsUsingPlugin)) {
                List<Project> installedProjects = new ArrayList<>();
                for (Project reactorProject : projectsUsingPlugin) {
                    State state = getState(reactorProject);
                    if (state == State.TO_BE_INSTALLED) {
                        Map<String, Object> pluginContext = session.getPluginContext(reactorProject);
                        ArtifactInstallerRequest request =
                                (ArtifactInstallerRequest) pluginContext.get(ArtifactInstallerRequest.class.getName());
                        try {
                            installProject(request);
                        } catch (MojoException e) {
                            logPartialInstallInventory(projectsUsingPlugin, installedProjects, reactorProject);
                            throw e;
                        }
                        installedProjects.add(reactorProject);
                        // exactly-once: transition state so a concurrent or repeated trigger (parallel
                        // build, or a second execution of the goal in the same session) skips completed work
                        putState(reactorProject, State.INSTALLED);
                    }
                }
            }
        }
    }

    /**
     * The contract documented on {@link #installAtEnd} is all-or-nothing; when a deferred install fails
     * mid-loop that contract can no longer be met, so leave an explicit inventory of which projects already
     * reached the local repository and which were skipped, instead of failing silently into a mixed state.
     */
    private void logPartialInstallInventory(
            List<Project> projectsUsingPlugin, List<Project> installedProjects, Project failedProject) {
        getLog().error("Failed to install " + gav(failedProject)
                + "; the local repository is in a partially installed state:");
        for (Project reactorProject : projectsUsingPlugin) {
            if (installedProjects.contains(reactorProject)) {
                getLog().error("  installed: " + gav(reactorProject));
            } else if (reactorProject == failedProject) {
                getLog().error("  failed: " + gav(reactorProject));
            } else if (getState(reactorProject) == State.TO_BE_INSTALLED) {
                getLog().error("  not installed: " + gav(reactorProject));
            }
        }
    }

    private static String gav(Project project) {
        return project.getGroupId() + ":" + project.getArtifactId() + ":" + project.getVersion();
    }

    private boolean allProjectsMarked(List<Project> projectsUsingPlugin) {
        return projectsUsingPlugin.stream().allMatch(this::hasState);
    }

    private void installProject(ArtifactInstallerRequest request) {
        try {
            getArtifactInstaller().install(request);
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
        ProjectManager projectManager = getProjectManager();
        Collection<ProducedArtifact> installables = projectManager.getAllArtifacts(project);
        Collection<ProducedArtifact> attachedArtifacts = projectManager.getAttachedArtifacts(project);

        getArtifactManager().setPath(project.getPomArtifact(), project.getPomPath());

        for (Artifact installable : installables) {
            if (!isValidPath(installable)) {
                if (installable == project.getMainArtifact().orElse(null)) {
                    if (attachedArtifacts.isEmpty()) {
                        throw new MojoException(
                                "The packaging for this project did not assign a file to the build artifact");
                    } else {
                        if (allowIncompleteProjects) {
                            getLog().warn("");
                            getLog().warn("The packaging plugin for this project did not assign");
                            getLog().warn(
                                            "a main file to the project but it has attachments. Change packaging to 'pom'.");
                            getLog().warn("");
                            getLog().warn("Incomplete projects like this will fail in future Maven versions!");
                            getLog().warn("");
                        } else {
                            throw new MojoException("The packaging plugin for this project did not assign "
                                    + "a main file to the project but it has attachments. Change packaging to 'pom'.");
                        }
                    }
                } else {
                    throw new MojoException("The packaging for this project did not assign "
                            + "a file to the attached artifact: " + installable);
                }
            }
        }

        return ArtifactInstallerRequest.build(session, installables);
    }

    private boolean isValidPath(Artifact a) {
        return getArtifactManager().getPath(a).filter(Files::isRegularFile).isPresent();
    }

    void setSkip(boolean skip) {
        this.skip = skip;
    }

    protected Log getLog() {
        return log;
    }

    private ArtifactInstaller getArtifactInstaller() {
        return session.getService(ArtifactInstaller.class);
    }

    private ArtifactManager getArtifactManager() {
        return session.getService(ArtifactManager.class);
    }

    private ProjectManager getProjectManager() {
        return session.getService(ProjectManager.class);
    }
}

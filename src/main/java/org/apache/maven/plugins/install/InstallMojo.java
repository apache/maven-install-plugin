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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.installation.InstallRequest;
import org.eclipse.aether.installation.InstallationException;

/**
 * Installs the project's main artifact, and any other artifacts attached by other plugins in the lifecycle, to the
 * local repository.
 *
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 */
@Mojo(name = "install", defaultPhase = LifecyclePhase.INSTALL, threadSafe = true)
public class InstallMojo extends AbstractMojo {
    @Component
    private RepositorySystem repositorySystem;

    @Parameter(defaultValue = "${session}", required = true, readonly = true)
    private MavenSession session;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${reactorProjects}", required = true, readonly = true)
    private List<MavenProject> reactorProjects;

    @Parameter(defaultValue = "${plugin}", required = true, readonly = true)
    private PluginDescriptor pluginDescriptor;

    /**
     * Whether every project should be installed during its own install-phase or at the end of the multimodule build. If
     * set to {@code true} and the build fails, none of the reactor projects is installed.
     * <strong>(experimental)</strong>
     *
     * @since 2.5
     */
    @Parameter(defaultValue = "false", property = "installAtEnd")
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
        getPluginContext().put(INSTALL_PROCESSED_MARKER, state.name());
    }

    private State getState(MavenProject project) {
        Map<String, Object> pluginContext = session.getPluginContext(pluginDescriptor, project);
        return State.valueOf((String) pluginContext.get(INSTALL_PROCESSED_MARKER));
    }

    private boolean hasState(MavenProject project) {
        Map<String, Object> pluginContext = session.getPluginContext(pluginDescriptor, project);
        return pluginContext.containsKey(INSTALL_PROCESSED_MARKER);
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Skipping artifact installation");
            putState(State.SKIPPED);
        } else {
            if (!installAtEnd) {
                InstallRequest request = new InstallRequest();
                processProject(project, request);
                installProject(request);
                putState(State.INSTALLED);
            } else {
                getLog().info("Deferring install for " + project.getGroupId() + ":" + project.getArtifactId() + ":"
                        + project.getVersion() + " at end");
                putState(State.TO_BE_INSTALLED);
            }
        }

        List<MavenProject> allProjectsUsingPlugin = getAllProjectsUsingPlugin();

        if (allProjectsMarked(allProjectsUsingPlugin)) {
            InstallRequest request = new InstallRequest();
            for (MavenProject reactorProject : allProjectsUsingPlugin) {
                State state = getState(reactorProject);
                if (state == State.TO_BE_INSTALLED) {
                    processProject(reactorProject, request);
                }
            }
            installProject(request);
        }
    }

    private boolean allProjectsMarked(List<MavenProject> allProjectsUsingPlugin) {
        for (MavenProject reactorProject : allProjectsUsingPlugin) {
            if (!hasState(reactorProject)) {
                return false;
            }
        }
        return true;
    }

    private List<MavenProject> getAllProjectsUsingPlugin() {
        ArrayList<MavenProject> result = new ArrayList<>();
        for (MavenProject reactorProject : reactorProjects) {
            if (hasExecution(reactorProject.getPlugin("org.apache.maven.plugins:maven-install-plugin"))) {
                result.add(reactorProject);
            }
        }
        return result;
    }

    private boolean hasExecution(Plugin plugin) {
        if (plugin == null) {
            return false;
        }

        for (PluginExecution execution : plugin.getExecutions()) {
            if (!execution.getGoals().isEmpty() && !"none".equalsIgnoreCase(execution.getPhase())) {
                return true;
            }
        }
        return false;
    }

    private void installProject(InstallRequest request) throws MojoExecutionException {
        try {
            repositorySystem.install(session.getRepositorySession(), request);
        } catch (InstallationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    /**
     * Processes passed in {@link MavenProject} and prepares content of {@link InstallRequest} out of it.
     *
     * @throws MojoExecutionException if project is badly set up.
     */
    private void processProject(MavenProject project, InstallRequest request) throws MojoExecutionException {
        if (isFile(project.getFile())) {
            request.addArtifact(RepositoryUtils.toArtifact(new ProjectArtifact(project)));
        } else {
            throw new MojoExecutionException("The project POM could not be attached");
        }

        if (!"pom".equals(project.getPackaging())) {
            org.apache.maven.artifact.Artifact mavenMainArtifact = project.getArtifact();
            if (isFile(mavenMainArtifact.getFile())) {
                request.addArtifact(RepositoryUtils.toArtifact(mavenMainArtifact));
            } else if (!project.getAttachedArtifacts().isEmpty()) {
                throw new MojoExecutionException("The packaging plugin for this project did not assign "
                        + "a main file to the project but it has attachments. Change packaging to 'pom'.");
            } else {
                throw new MojoExecutionException(
                        "The packaging for this project did not assign " + "a file to the build artifact");
            }
        }

        for (org.apache.maven.artifact.Artifact attached : project.getAttachedArtifacts()) {
            getLog().debug("Attaching for install: " + attached.getId());
            request.addArtifact(RepositoryUtils.toArtifact(attached));
        }
    }

    private boolean isFile(File file) {
        return file != null && file.isFile();
    }
}

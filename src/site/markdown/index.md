---
title: Introduction
author: 
  - Allan Ramirez
date: 2013-07-22
---

<!--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

# Apache Maven Install Plugin
The Install Plugin adds artifacts to the local repository during the `install` phase. It uses the POM data to find the correct location in the local repository. The POM data includes the `groupId`, the `artifactId`, and the `version`.

The local repository is the local cache for the artifacts in a build. It is in the user's home directory `(~/.m2/repository)` by default.

You can change the location with the `<localRepository>` element in `~/.m2/settings.xml`.

## Goals Overview

The Install Plugin has 3 goals:

- [install:install](./install-mojo.html) installs the main artifact of the project. It also installs the project POM and the attached artifacts such as `sources` and `javadoc` JARs.
- [install:install-file](./install-file-mojo.html) installs an externally created artifact and its POM into the local repository. You can provide the artifact data with the `pomFile` parameter or with command line parameters.
- [install:help](./help-mojo.html) displays help information on the maven-install-plugin.

## Important Note for Version 3.0.0+

The [install:install](./install-mojo.html) goal no longer creates checksums with the `-DcreateChecksum=true` parameter. For details, see [MINSTALL-143](https://issues.apache.org/jira/browse/MINSTALL-143).

## Usage

See the [usage page](./usage.html) for general instructions. See the examples below for specific use cases.

If you have a question, read the [FAQ](./faq.html) first. Contact the [user mailing list](./mailing-lists.html) if you need more help. The mailing list stores old posts. An old thread can contain the answer to your question. Also browse the [mail archive](./mailing-lists.html).

If the plugin is missing a feature, file a feature request. If the plugin has a defect, file a bug report. Use the [issue tracker](./issue-management.html) to file requests and reports. Describe your problem in detail. For bug fixes, the developers must reproduce your problem. Attach debug logs, POMs, or a small demo project to the issue.

Attach a patch to the issue if you have one. You can check out the project from the [source repository](./scm.html). Read the [guide to helping with Maven](http://maven.apache.org/guides/development/guide-helping.html) for more information.

## Examples

The following examples show some usages of the Maven Install Plugin:

- [Installing a Custom POM](./examples/custom-pom-installation.html)
- [Generating a Generic POM](./examples/generic-pom-generation.html)
- [Installing an Artifact to a Specific Local Repository Path](./examples/specific-local-repo.html)
- [Installing Secondary Artifacts](./examples/installing-secondary-artifacts.html)

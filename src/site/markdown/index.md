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
The Install Plugin is used during the `install` phase to add artifact(s) to the local repository. The Install Plugin uses the information in the POM (groupId, artifactId, version) to determine the proper location for the artifact within the local repository.

The local repository is the local cache where all artifacts needed for the build are stored. By default, it is located within the user's home directory `(~/.m2/repository)` but the location can be configured in `~/.m2/settings.xml` using the `<localRepository>` element.

## Goals Overview

The Install Plugin has 3 goals:

- [install:install](./install-mojo.html) is used to automatically install the project's main artifact (the JAR, WAR or EAR), its POM and any attached artifacts (sources, javadoc, etc) produced by a particular project.
- [install:install-file](./install-file-mojo.html) is mostly used to install an externally created artifact into the local repository, along with its POM. In that case the project information can be taken from an optionally specified pomFile, but can also be given using command line parameters.
- [install:help](./help-mojo.html) displays help information on maven-install-plugin.
## Important Note for Version 3.0.0+

The [install:install](./install-mojo.html) goal does not support creating checksums anymore via `-DcreateChecksum=true` cause this option has been removed. Details can be found in [MINSTALL-143](https://issues.apache.org/jira/browse/MINSTALL-143).

## Usage

General instructions on how to use the Install Plugin can be found on the [usage page](./usage.html). Some more specific use cases are described in the examples given below.

In case you still have questions regarding the plugin's usage, please have a look at the [FAQ](./faq.html) and feel free to contact the [user mailing list](./mailing-lists.html). The posts to the mailing list are archived and could already contain the answer to your question as part of an older thread. Hence, it is also worth browsing/searching the [mail archive](./mailing-lists.html).

If you feel like the plugin is missing a feature or has a defect, you can fill a feature request or bug report in our [issue tracker](./issue-management.html). When creating a new issue, please provide a comprehensive description of your concern. Especially for fixing bugs it is crucial that the developers can reproduce your problem. For this reason, entire debug logs, POMs or most preferably little demo projects attached to the issue are very much appreciated. Of course, patches are welcome, too. Contributors can check out the project from our [source repository](./scm.html) and will find supplementary information in the [guide to helping with Maven](http://maven.apache.org/guides/development/guide-helping.html).

## Examples

To provide you with better understanding on some usages of the Maven Install Plugin, you can take a look into the following examples:

- [Installing a Custom POM](./examples/custom-pom-installation.html)
- [Generating a Generic POM](./examples/generic-pom-generation.html)
- [Installing an Artifact to a Specific Local Repository Path](./examples/specific-local-repo.html)
- [Installing Secondary Artifacts](./examples/installing-secondary-artifacts.html)

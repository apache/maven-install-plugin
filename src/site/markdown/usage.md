---
title: Usage
author: 
  - Allan Ramirez
  - Robert Scholte
date: 2013-07-20
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

# Usage

Apache Maven has a two-level strategy to resolve and distribute files. These files are the artifacts. The `local repository` is the first level. It is the artifact cache on your system. The default location is `${user.home}/.m2/repository`. Maven looks in the local cache for artifacts first.

If Maven cannot find an artifact, it will access the remote repositories. Maven stores the artifact in the local repository once it finds it. The artifact is then available for current and future usage.

With the `maven-install-plugin`, you can put your artifacts in the local repository. To upload artifacts to a remote repository, use the [maven-deploy-plugin](http://maven.apache.org/plugins/maven-deploy-plugin/).

## The `install:install` goal

In most cases, the `install:install` goal needs no configuration. It needs the project POM and the artifact file. It runs during the `install` phase of the default build lifecycle.

```unknown
mvn install
```

## The `install:install-file` goal

The `install:install-file` goal installs artifacts that Maven did not build into the local repository. The development team can provide a POM for the artifact. The POM is not required. The following list shows the available parameters for the `install-file` goal:

```unknown
mvn install:install-file -Dfile=your-artifact-1.0.jar \
                         [-DpomFile=your-pom.xml] \
                         [-Dsources=src.jar] \
                         [-Djavadoc=apidocs.jar] \
                         [-DgroupId=org.some.group] \
                         [-DartifactId=your-artifact] \
                         [-Dversion=1.0] \
                         [-Dpackaging=jar] \
                         [-Dclassifier=sources] \
                         [-DgeneratePom=true] \
                         [-DcreateChecksum=true]
```

- The groupId, artifactId, version, and packaging parameters define the file to install. You can get these values from the pomFile parameter, from the pom.xml inside the artifact, or from the command line. If you do not specify the groupId for the current project or on the command line, Maven uses the parent groupId. This only applies when the pomFile contains a _parent_ section.
- The optional `classifier` parameter installs a secondary artifact, for example a `javadoc` or `sources` JAR. See [Installing Secondary Artifacts](./examples/installing-secondary-artifacts.html) for more information. If you do not give a classifier, Maven treats the file as the main artifact.

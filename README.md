<!---
 Licensed to the Apache Software Foundation (ASF) under one or more
 contributor license agreements.  See the NOTICE file distributed with
 this work for additional information regarding copyright ownership.
 The ASF licenses this file to You under the Apache License, Version 2.0
 (the "License"); you may not use this file except in compliance with
 the License.  You may obtain a copy of the License at

      http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
-->
# Apache Maven Install Plugin

[![Apache License, Version 2.0, January 2004](https://img.shields.io/github/license/apache/maven.svg?label=License)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/org.apache.maven.plugins/maven-install-plugin.svg?label=Maven%20Central&versionPrefix=4.)](https://central.sonatype.com/artifact/org.apache.maven.plugins/maven-install-plugin)
[![Reproducible Builds](https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/jvm-repo-rebuild/reproducible-central/master/content/org/apache/maven/plugins/maven-install-plugin/badge.json)](https://github.com/jvm-repo-rebuild/reproducible-central/blob/master/content/org/apache/maven/plugins/maven-install-plugin/README.md)
[![Verify](https://github.com/apache/maven-install-plugin/actions/workflows/maven-verify.yml/badge.svg?branch=maven-install-plugin-4.x)](https://github.com/apache/maven-install-plugin/actions/workflows/maven-verify.yml)

The Install Plugin copies a project's artifacts into the local Maven repository (`install:install`) and installs externally built files there (`install:install-file`). User documentation: <https://maven.apache.org/plugins/maven-install-plugin/>.

This branch (`maven-install-plugin-4.x`) is the 4.x line. It requires Maven 4 and Java 17. The 3.x line lives on [`master`](https://github.com/apache/maven-install-plugin/tree/master).

## Build

```bash
mvn verify
```

Add `-Prun-its` to run the integration tests in `src/it`.

## Contribute

Report bugs and feature requests in [GitHub issues](https://github.com/apache/maven-install-plugin/issues) and send pull requests against `maven-install-plugin-4.x`. Repository conventions for tests, compatibility and code style are in [AGENTS.md](AGENTS.md). Report security issues privately as described in [SECURITY.md](SECURITY.md).

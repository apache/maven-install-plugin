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

# Agent guide for maven-install-plugin

This file is read by automated agents (security scanners, code
analyzers, AI assistants) operating on this repository.

## Security

Security model: [SECURITY.md](./SECURITY.md)

Agents that scan this repository should consult `SECURITY.md` and the
threat model it links before reporting issues.

## Compatibility

This branch is the 3.x line: it requires Maven 3.9.0 or later and Java 8. Code against the Maven 3.9 and Resolver 1.9 APIs only; Maven 4 APIs and code from the 4.x branch do not belong here.

- Goal names, parameter names and user properties are public API. Never rename
  or remove one: `maven.install.skip`, `installAtEnd`, `allowIncompleteProjects`,
  and the `install-file` properties (`file`, `pomFile`, `groupId`, `artifactId`,
  `version`, `packaging`, `classifier`, `sources`, `javadoc`, `generatePom`,
  `localRepositoryPath`, `extension`).
- Site pages under `src/site` are deep-linked from outside: do not rename a page
  or change a heading's anchor.
- Fixes land here first and are forward-ported to `maven-install-plugin-4.x`. Known differences between the lines: `installAtEnd`
  defaults to `false` on 3.x and `true` on 4.x, and the `install-file`
  `extension` parameter exists only on 3.x.

## Coding

Use test-first programming. Before fixing a bug or implementing a feature,
write a test that exposes the bug and verify the test fails.

The bytecode floor is Java 8. Before submitting a PR, run `mvn spotless:apply`
to format the files, then `mvn verify`. Run Spotless with JDK 17 or 21.

## Tests

Unit tests are JUnit 5 (Jupiter) and live in `src/test/java`. New test code must not add JUnit 4, the vintage engine, Hamcrest, or `org.codehaus.plexus.util`; use JDK/Groovy APIs instead. Name tests after the behaviour they check, not after JIRA keys.

Integration tests in `src/it` are reserved for behaviour that needs a real Maven build: reactor and deferred installation, `-T2` parallel builds, `-f` alternate POMs, embedded-POM execution and the `setup-*` support projects. Everything a mojo unit test can assert with a mocked session/repository belongs in `src/test`. Keep `verify.groovy` scripts in plain `assert` form.

The Maven 4 branch (`maven-install-plugin-4.x`) uses the Maven 4 `@MojoTest` harness. Its `ArtifactInstaller` is mocked, so `maven-metadata-local.xml` is not written during unit tests; assert the coordinate-derived paths from the install request instead of a physical file. The 3.x line's harness writes real files. Both branches keep the same IT names and test-case names, but do not share API-specific test code: 4.x stays Maven 4 only.

Run `mvn -Prun-its verify` for the full build, or `mvn -o -B test spotless:check` for the unit suite and formatting.

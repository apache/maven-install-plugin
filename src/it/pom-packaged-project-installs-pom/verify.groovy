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

// jar:jar on a pom-packaged project sets project.getArtifact().getFile() to the produced JAR.
// A subsequent install:install must install the POM, not that JAR. Maven 4 also installs a
// -build.pom next to the consumer POM, so check every installed .pom file.

File dir = new File(localRepositoryPath, "org/apache/maven/its/install/minstall315/test/1.0")
File installed = new File(dir, "test-1.0.pom")
assert installed.isFile() : "Installed file not found: " + installed

// A JAR (ZIP) starts with the magic bytes PK (0x50 0x4B).
for ( File pom : dir.listFiles().findAll { it.name.endsWith( ".pom" ) } ) {
    byte[] magic = new byte[2]
    pom.withInputStream { it.read( magic ) }
    assert !( magic[0] == 0x50 && magic[1] == 0x4B ) :
    "install:install installed a JAR (ZIP) instead of the POM as " + pom.name
}

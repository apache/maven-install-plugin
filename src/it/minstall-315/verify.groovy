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

// MINSTALL-315: jar:jar on a pom-packaged project sets project.getArtifact().getFile() to the produced JAR.
// A subsequent install:install must install the POM, not that JAR.

File installed = new File( localRepositoryPath,
    "org/apache/maven/its/install/minstall315/test/1.0/test-1.0.pom" )

if ( !installed.isFile() ) {
    throw new FileNotFoundException( "Installed file not found: " + installed )
}

// The installed file must not be a JAR (ZIP). Check the magic bytes: PK\x03\x04 = 0x50 0x4B 0x03 0x04.
byte[] magic = new byte[4]
new FileInputStream( installed ).with { stream ->
    stream.read( magic )
}

if ( magic[0] == 0x50 && magic[1] == 0x4B ) {
    throw new Exception(
        "MINSTALL-315 regression: install:install installed a JAR (ZIP) instead of the POM for a pom-packaged project" )
}

return true

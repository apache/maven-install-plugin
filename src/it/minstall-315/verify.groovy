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

// The installed file must be an XML POM, not a JAR (ZIP). Check the magic bytes.
byte[] magic = new byte[4]
new FileInputStream( installed ).with { stream ->
    stream.read( magic )
}

// ZIP magic bytes: PK\x03\x04 (0x50 0x4B 0x03 0x04)
if ( magic[0] == 0x50 && magic[1] == 0x4B ) {
    throw new Exception(
        "MINSTALL-315 regression: install:install installed a JAR (ZIP) instead of the POM for a pom-packaged project" )
}

// Must start with XML declaration or opening tag
String head = new String( magic )
if ( !head.startsWith( "<" ) && !head.startsWith( "\xEF" ) ) {  // UTF-8 BOM or plain XML
    // Also accept UTF-8 BOM (EF BB BF 3C)
    if ( !( magic[0] == (byte)0xEF && magic[1] == (byte)0xBB ) ) {
        if ( !installed.text.trim().startsWith( "<" ) ) {
            throw new Exception( "Installed file does not look like XML: " + installed )
        }
    }
}

return true

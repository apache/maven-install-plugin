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

import java.io.*;
import java.util.*;

String groupPath = "org/apache/maven/its/install/minstall315/test/1.0";

// The POM must be present in the local repository
File pom = new File( localRepositoryPath, groupPath + "/test-1.0.pom" );
System.out.println( "Checking for POM: " + pom );
assert pom.isFile() : "POM must be installed: " + pom;

// The installed file must be an XML/POM, not a JAR.
// ZIP/JAR files start with magic bytes 0x50 0x4B ("PK").
byte[] magic = new byte[2];
pom.withInputStream { is -> is.read( magic ) }
assert !(magic[0] == 0x50 && magic[1] == 0x4b) :
    "Installed artifact must be a POM (XML), not a JAR (ZIP). MINSTALL-315 regression detected!"

// No JAR should have been installed at the POM coordinates
File jar = new File( localRepositoryPath, groupPath + "/test-1.0.jar" );
System.out.println( "Checking JAR is absent: " + jar );
assert !jar.isFile() : "No JAR should be installed for a pom-packaged project: " + jar;

return true;

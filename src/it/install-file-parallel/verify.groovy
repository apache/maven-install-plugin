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

// MINSTALL-205: install:install-file must not trigger an "aggregator mojo" warning
// when two modules invoke it concurrently in a parallel (-T 2) build.

import java.io.*;

// 1. Both artifacts must have been installed successfully
String[] paths = [
    "org/apache/maven/its/install/minstall205/external-module1/1.0/external-module1-1.0.jar",
    "org/apache/maven/its/install/minstall205/external-module1/1.0/external-module1-1.0.pom",
    "org/apache/maven/its/install/minstall205/external-module2/1.0/external-module2-1.0.jar",
    "org/apache/maven/its/install/minstall205/external-module2/1.0/external-module2-1.0.pom",
];

for ( String path : paths ) {
    File file = new File( localRepositoryPath, path );
    System.out.println( "Checking for existence of " + file );
    if ( !file.isFile() ) {
        throw new FileNotFoundException( "Missing: " + file.getAbsolutePath() );
    }
}

// 2. The build log must NOT contain the "aggregator mojo already being executed" warning
File buildLog = new File( basedir, "build.log" );
String log = buildLog.text;

if ( log.contains( "aggregator mojo is already being executed" ) ) {
    throw new AssertionError(
        "Build log contains the 'aggregator mojo is already being executed' warning, " +
        "but install-file should no longer be an aggregator (MINSTALL-205)." );
}

return true;

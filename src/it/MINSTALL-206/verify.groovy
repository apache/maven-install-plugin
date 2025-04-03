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

// Verify that both modules were built successfully
File buildLog = new File(basedir, 'build.log')
assert buildLog.exists()

// Verify that the module with phase=none was skipped
// We don't need to check for the install plugin output since it might not appear with phase=none
assert !buildLog.text.contains("The packaging for this project did not assign a file to the build artifact")

// Verify that the module with skip=true was skipped
assert buildLog.text.contains("[INFO] Skipping artifact installation")

// Verify that the parent POM was installed
assert new File(localRepositoryPath, "org/apache/maven/its/install/minstall206/parent/1.0/parent-1.0.pom").exists()

// Verify that neither of the modules were installed
assert !new File(localRepositoryPath, "org/apache/maven/its/install/minstall206/module-phase-none/1.0/module-phase-none-1.0.jar").exists()
assert !new File(localRepositoryPath, "org/apache/maven/its/install/minstall206/module-skip-true/1.0/module-skip-true-1.0.jar").exists()

return true

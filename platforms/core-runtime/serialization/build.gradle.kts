/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("gradlebuild.distribution.implementation-java")
    id("gradlebuild.publish-public-libraries")
}

description = "Tools to serialize data"

gradleModule {
    targetRuntimes {
        usedInWorkers = true
    }
}

jvmCompile {
    compilations {
        named("main") {
            usesFutureStdlib = true
        }
    }
}

dependencies {
    api(projects.classloaders)
    api(projects.hashing)
    api(projects.stdlibJavaExtensions)

    api(libs.guava)
    api(libs.jspecify)

    implementation(projects.io)

    implementation(libs.commonsIo)
    implementation(libs.fastutil)
    implementation(libs.jsr305)
    implementation(libs.kryo)
    implementation(libs.slf4jApi)

    compileOnly(libs.errorProneAnnotations)
}

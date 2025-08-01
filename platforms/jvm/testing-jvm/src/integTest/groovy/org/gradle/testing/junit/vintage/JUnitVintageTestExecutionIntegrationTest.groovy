/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.junit.vintage

import org.gradle.integtests.fixtures.DefaultTestExecutionResult
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.TestClassExecutionResult
import org.gradle.integtests.fixtures.TestExecutionResult
import org.gradle.testing.junit.AbstractJUnitTestExecutionIntegrationTest

import static org.gradle.testing.fixture.JUnitCoverage.JUNIT_VINTAGE
import static org.gradle.testing.fixture.JUnitCoverage.LATEST_JUNIT4_VERSION
import static org.hamcrest.CoreMatchers.containsString

@TargetCoverage({ JUNIT_VINTAGE })
class JUnitVintageTestExecutionIntegrationTest extends AbstractJUnitTestExecutionIntegrationTest implements JUnitVintageMultiVersionTest {
    @Override
    String getJUnitVersionAssertion() {
        return "assertEquals(\"${LATEST_JUNIT4_VERSION}\", new org.junit.runner.JUnitCore().getVersion());"
    }

    @Override
    TestClassExecutionResult assertFailedToExecute(TestExecutionResult testResult, String testClassName) {
        return testResult.testClassStartsWith('Gradle Test Executor')
            .assertTestFailed("failed to execute tests", containsString("Could not execute test class '${testClassName}'"))
    }

    @Override
    String getStableEnvironmentDependencies() {
        return super.getStableEnvironmentDependencies() + """
            testImplementation 'junit:junit:${LATEST_JUNIT4_VERSION}'
        """
    }

    def "tries to execute unparseable test classes"() {
        given:
        file('build/classes/java/test/com/example/Foo.class').text = "invalid class file"
        buildFile << """
            apply plugin: 'java'
            ${mavenCentralRepository()}
            dependencies {
                ${testFrameworkDependencies}
            }
            test.${configureTestFramework}
        """

        when:
        fails('test', '-x', 'compileTestJava')

        then:
        failure.assertHasCause("Test process encountered an unexpected problem.")
        failure.assertHasCause("Could not execute test class 'com.example.Foo'.")
        DefaultTestExecutionResult testResult = new DefaultTestExecutionResult(testDirectory)
        assertFailedToExecute(testResult, 'com.example.Foo').assertTestCount(1, 1, 0)
    }
}

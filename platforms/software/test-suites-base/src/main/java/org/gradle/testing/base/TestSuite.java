/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.testing.base;

import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Incubating;
import org.gradle.api.Named;

/**
 * Base test suite component. A test suite is a collection of tests.
 *
 * @since 7.3
 */
@Incubating
public interface TestSuite extends Named {
    /**
     * Available targets for this test suite.
     */
    DomainObjectCollection<? extends TestSuiteTarget> getTargets();
}

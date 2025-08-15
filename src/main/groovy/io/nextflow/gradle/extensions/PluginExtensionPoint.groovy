/*
 * Copyright 2013-2024, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.nextflow.gradle.extensions

import groovy.transform.PackageScope
import org.pf4j.ExtensionPoint

/**
 * Define plugin extension points. A plugin can provide extension methods
 * to add custom channel factories, channel operators and custom function
 * in the including context.
 *
 * The extending subclass should mark the extension methods with the
 * corresponding annotation {@link Factory}, {@link Operator} and {@link Function}
 *
 * Note: This returns Object for session to avoid coupling the gradle plugin 
 * to Nextflow's internal Session class. Implementations should expect a 
 * nextflow.Session instance.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class PluginExtensionPoint implements ExtensionPoint {

    private boolean initialised

    synchronized void checkInit(Object session) {
        if( !initialised ) {
            init(session)
            initialised = true
        }
    }

    /**
     * Channel factory initialization. This method is invoked one and only once before
     * the before target extension method is called.
     *
     * @param session The current nextflow Session (passed as Object to avoid coupling)
     */
    abstract protected void init(Object session)

}
package io.nextflow.gradle.registry

import org.gradle.api.GradleException

/**
 * Custom exception class for registry publish task
 */
class RegistryPublishException extends GradleException{
    RegistryPublishException(String s) {
        super(s)
    }

    RegistryPublishException(String string, Throwable e) {
        super(string,e)
    }
}

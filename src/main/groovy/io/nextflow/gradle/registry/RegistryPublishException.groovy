package io.nextflow.gradle.registry

/**
 * Custom exception class for registry publish task
 */
class RegistryPublishException extends Exception{
    RegistryPublishException(String s) {
        super(s)
    }
}

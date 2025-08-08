package io.nextflow.gradle.registry

import org.gradle.api.GradleException

/**
 * Exception thrown when plugin registry operations fail.
 * 
 * This exception is used to wrap and report errors that occur
 * during plugin upload to a registry, including network errors,
 * authentication failures, and API response errors.
 */
class RegistryReleaseException extends GradleException{
    /**
     * Creates a new registry release exception with the given message.
     * 
     * @param message The error message
     */
    RegistryReleaseException(String message) {
        super(message)
    }

    /**
     * Creates a new registry release exception with the given message and cause.
     * 
     * @param message The error message
     * @param cause The underlying exception that caused this error
     */
    RegistryReleaseException(String message, Throwable cause) {
        super(message, cause)
    }
}

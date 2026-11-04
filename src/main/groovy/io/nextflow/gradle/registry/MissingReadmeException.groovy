package io.nextflow.gradle.registry

/**
 * Exception thrown when README.md file is not found in the project root directory.
 *
 * This exception provides a detailed error message explaining what sections
 * are required in the README.md file for plugin registry submission.
 */
class MissingReadmeException extends RegistryReleaseException {

    private static final String MESSAGE = """
            |README.md file not found in the project root directory.
            |
            |Please create a README.md file with the following required sections:
            |  1. Summary: Explain what the plugin does
            |  2. Get Started: Setup and configuration instructions
            |  3. Examples: Code examples with code blocks
            |  4. License: Specify the plugin's license (e.g., Apache 2.0, MIT, GPL)
            |
            |Optional sections (include if relevant):
            |  - What's new: Recent changes or new features
            |  - Breaking changes: Incompatible changes users should be aware of
            |
            |Requirements:
            |  - Content must be meaningful (no placeholder text like TODO, TBD, Lorem ipsum)
            |  - Content must be in English
            |
            |This file will be used as the plugin description in the registry.
            """.stripMargin().trim()

    MissingReadmeException() {
        super(MESSAGE)
    }
}

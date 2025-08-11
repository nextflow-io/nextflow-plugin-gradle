package io.nextflow.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * Unit tests for NextflowPluginConfig validation
 */
class NextflowPluginConfigTest extends Specification {

    Project project
    NextflowPluginConfig config

    def setup() {
        project = ProjectBuilder.builder()
            .withName('test-plugin')
            .build()
        project.version = '1.0.0'
        config = new NextflowPluginConfig(project)
    }

    def "should pass validation with valid configuration"() {
        given:
        config.nextflowVersion = '24.04.0'
        config.className = 'com.example.TestPlugin'
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        noExceptionThrown()
    }

    def "should fail when nextflowVersion is null"() {
        given:
        config.nextflowVersion = null
        config.className = 'com.example.TestPlugin'
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == 'nextflowPlugin.nextflowVersion not specified'
    }

    def "should fail when nextflowVersion is empty"() {
        given:
        config.nextflowVersion = ''
        config.className = 'com.example.TestPlugin'
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == 'nextflowPlugin.nextflowVersion not specified'
    }

    def "should fail when nextflowVersion is not valid semver"() {
        given:
        config.nextflowVersion = invalidVersion
        config.className = 'com.example.TestPlugin'
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == "nextflowPlugin.nextflowVersion '${invalidVersion}' is invalid. Must be a valid semantic version (semver) string"

        where:
        invalidVersion << ['not.a.version', '1', '1.0', 'v1.0.0', '1.0.0.0', 'latest']
    }

    def "should pass when nextflowVersion is valid semver"() {
        given:
        config.nextflowVersion = validVersion
        config.className = 'com.example.TestPlugin'
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        noExceptionThrown()

        where:
        validVersion << ['1.0.0', '24.04.0', '1.2.3-alpha', '1.0.0-beta.1', '2.0.0-rc.1', '25.04.0-edge']
    }

    def "should fail when className is null"() {
        given:
        config.nextflowVersion = '24.04.0'
        config.className = null
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == 'nextflowPlugin.className not specified'
    }

    def "should fail when className is empty"() {
        given:
        config.nextflowVersion = '24.04.0'
        config.className = ''
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == 'nextflowPlugin.className not specified'
    }

    def "should fail when className is not valid Java FQCN"() {
        given:
        config.nextflowVersion = '24.04.0'
        config.className = invalidClassName
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == "nextflowPlugin.className '${invalidClassName}' is invalid. Must be a valid Java fully qualified class name with package"

        where:
        invalidClassName << [
            'InvalidClassName',          // no package - single class names are invalid
            'com.example.',              // trailing dot
            '.com.example.TestPlugin',   // leading dot
            'com..example.TestPlugin',   // double dot
            'com.example..TestPlugin',   // double dot
            'com.123example.TestPlugin', // package starts with number
            'com.example.123Plugin',     // class starts with number
            'com.example.Test-Plugin',   // invalid character
            'com.example.Test Plugin',   // space
            'com.example.Test@Plugin'    // special character
        ]
    }

    def "should pass when className is valid Java FQCN"() {
        given:
        config.nextflowVersion = '24.04.0'
        config.className = validClassName
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        noExceptionThrown()

        where:
        validClassName << [
            'com.example.TestPlugin',
            'io.nextflow.plugins.MyPlugin',
            'org.springframework.boot.Application',
            'com.company.product.module.ClassName',
            'com.example.Test$InnerClass',
            'com.example._ValidClass',
            'com.example.$ValidClass',
            'com.example.TestPlugin123',
            '_root.TestPlugin',
            '$root.TestPlugin'
        ]
    }

    def "should fail when provider is null"() {
        given:
        config.nextflowVersion = '24.04.0'
        config.className = 'com.example.TestPlugin'
        config.provider = null

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == 'nextflowPlugin.provider not specified'
    }

    def "should fail when provider is empty string"() {
        given:
        config.nextflowVersion = '24.04.0'
        config.className = 'com.example.TestPlugin'
        config.provider = ''

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == 'nextflowPlugin.provider not specified'
    }

    def "should fail when provider is empty or whitespace"() {
        given:
        config.nextflowVersion = '24.04.0'
        config.className = 'com.example.TestPlugin'
        config.provider = emptyProvider

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == 'nextflowPlugin.provider cannot be empty'

        where:
        emptyProvider << ['   ', '\t', '\n', ' \t \n ']
    }

    def "should pass when provider is valid"() {
        given:
        config.nextflowVersion = '24.04.0'
        config.className = 'com.example.TestPlugin'
        config.provider = validProvider

        when:
        config.validate()

        then:
        noExceptionThrown()

        where:
        validProvider << ['Test Author', 'Nextflow', 'Company Inc.', 'John Doe', 'team@company.com']
    }

    def "should fail when project version is not valid semver"() {
        given:
        project.version = 'invalid-version'
        config.nextflowVersion = '24.04.0'
        config.className = 'com.example.TestPlugin'
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == "Plugin version 'invalid-version' is invalid. Plugin versions must be a valid semantic version (semver) string"
    }

    def "should fail when project name is invalid plugin id"() {
        given:
        project = ProjectBuilder.builder()
            .withName('bad_name')  // underscore not allowed
            .build()
        project.version = '1.0.0'
        config = new NextflowPluginConfig(project)
        config.nextflowVersion = '24.04.0'
        config.className = 'com.example.TestPlugin'
        config.provider = 'Test Author'

        when:
        config.validate()

        then:
        RuntimeException ex = thrown()
        ex.message == "Plugin id 'bad_name' is invalid. Plugin ids can contain numbers, letters, and the '-' symbol"
    }

    def "should pass with all valid configuration including edge cases"() {
        given:
        config.nextflowVersion = '25.04.0-edge'
        config.className = 'io.nextflow.plugin.test.MyTestPlugin$InnerClass'
        config.provider = 'Nextflow Community'

        when:
        config.validate()

        then:
        noExceptionThrown()
    }
}
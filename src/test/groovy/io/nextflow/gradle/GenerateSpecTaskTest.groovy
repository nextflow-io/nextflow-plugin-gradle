package io.nextflow.gradle

import spock.lang.Specification

/**
 *
 * @author Ben Sherman <bentshermann@gmail.com>
 */
class GenerateSpecTaskTest extends Specification {

    def 'should determine whether Nextflow version is >=25.09.0-edge' () {
        expect:
        GenerateSpecTask.isVersionSupported(VERSION) == RESULT

        where:
        VERSION         | RESULT
        '24.10.0'       | false
        '25.04.0'       | false
        '25.04.1'       | false
        '25.09.0-edge'  | true
        '25.09.1-edge'  | true
        '25.10.0'       | true
        '25.10.1'       | true
        '26.01.0-edge'  | true
        '26.04.0'       | true
        '26.04.6'       | true
        '27.10.0'       | true
        '25.09'         | false
        'foo'           | false
    }

}

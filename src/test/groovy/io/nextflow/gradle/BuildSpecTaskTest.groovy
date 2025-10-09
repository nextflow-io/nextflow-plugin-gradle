package io.nextflow.gradle

import spock.lang.Specification

/**
 *
 * @author Ben Sherman <bentshermann@gmail.com>
 */
class BuildSpecTaskTest extends Specification {

    def 'should determine whether Nextflow version is >=25.09.0-edge' () {
        given:
        def parts = VERSION.split(/\./, 3)
        def major = Integer.parseInt(parts[0])
        def minor = Integer.parseInt(parts[1])
        def isSupported = major >= 25 && minor >= 9

        expect:
        isSupported == RESULT

        where:
        VERSION         | RESULT
        '25.04.0'       | false
        '25.04.1'       | false
        '25.09.0-edge'  | true
        '25.09.1-edge'  | true
        '25.10.0'       | true
        '25.10.1'       | true
    }

}

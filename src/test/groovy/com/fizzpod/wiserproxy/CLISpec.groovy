package com.fizzpod.wiserproxy

import spock.lang.Specification

class CLISpec extends Specification {

    def "should parse default arguments when none are provided"() {
        when:
        def options = CLI.parse([] as String[])

        then:
        options != null
        options.port == 9080
        options.url == "http://wiser.local"
        !options.secret
    }

    def "should override port with -p or --port"() {
        when:
        def options1 = CLI.parse(["-p", "1234"] as String[])
        def options2 = CLI.parse(["--port", "5678"] as String[])

        then:
        options1.port == 1234
        options2.port == 5678
    }

    def "should override url with -u or --url"() {
        when:
        def options1 = CLI.parse(["-u", "http://hub1"] as String[])
        def options2 = CLI.parse(["--url", "https://hub2"] as String[])

        then:
        options1.url == "http://hub1"
        options2.url == "https://hub2"
    }

    def "should override secret with -s or --secret"() {
        when:
        def options1 = CLI.parse(["-s", "secret1"] as String[])
        def options2 = CLI.parse(["--secret", "secret2"] as String[])

        then:
        options1.secret == "secret1"
        options2.secret == "secret2"
    }

    def "should detect help flag"() {
        when:
        def options1 = CLI.parse(["-h"] as String[])
        def options2 = CLI.parse(["--help"] as String[])

        then:
        options1.h
        options2.h
    }
}

package com.fizzpod.wiserproxy

import groovy.cli.picocli.CliBuilder
import groovy.cli.picocli.OptionAccessor

public class CLI {

    private static CliBuilder getParser() {
        def parser = new CliBuilder(usage: 'wiser')
        parser.h(longOpt: 'help', 'Print this message')
        parser.p(longOpt: 'port', type: Integer, args: 1, argName: 'port', 'port for the server to listen on', defaultValue: System.getenv("WISER_PROXY_PORT") ?: '9080')
        parser.s(longOpt: 'secret', args: 1, argName: 'secret', 'Your wiser secret token', defaultValue: System.getenv("WISER_SECRET"))
        parser.u(longOpt: 'url', args: 1, argName: 'url', 'The URL of the wiser hub', defaultValue: System.getenv("WISER_URL") ?: 'http://wiser.local')
        return parser
    }

    public static OptionAccessor parse(String[] args) {
        return getParser().parse(args)
    }

    public static void usage() {
        getParser().usage()
    }
}
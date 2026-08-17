package com.fizzpod.wiserproxy;

import groovy.cli.picocli.CliBuilder;

public class CLI {
    private static CliBuilder getParser() {
        def cli = new CliBuilder(usage:'wiser');
        cli.h(longOpt:'help', 'Print this message')
        cli.p(longOpt:'port', type: Integer, args:1, argName:'port', 'port for the server to listen on', defaultValue: System.getenv("WISER_PROXY_PORT") != null? System.getenv("WISER_PROXY_PORT"): '9080')
        cli.s(longOpt:'secret', args:1, argName: 'secret', 'Your wiser secret token', defaultValue: System.getenv().get("WISER_SECRET"))
        cli.u(longOpt:'url', args:1, argName: 'url', 'The URL of the wiser hub', defaultValue: System.getenv("WISER_URL") != null? System.getenv("WISER_URL") : "wiser.local")
        cli.c(longOpt:'cache-ttl', type: Integer, args:1, argName: 'cache-ttl', 'Cache TTL in seconds for GET requests (0 to disable)', defaultValue: System.getenv("WISER_CACHE_TTL") != null? System.getenv("WISER_CACHE_TTL") : '5')
        return cli
    }

    public static def parse = { String[] args ->
        return getParser().parse(args)
    }

    public static def usage = {
        getParser().usage()
    }

}
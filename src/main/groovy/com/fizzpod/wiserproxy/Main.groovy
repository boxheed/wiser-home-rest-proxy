package com.fizzpod.wiserproxy

import org.apache.groovy.contracts.*
import org.tinylog.*

public class Main {

    public static void main(String[] args) {

        def options = CLI.parse(args)

        if(options != null) {
            try {
                if(options.h) {
                    CLI.usage()
                } else {
                    WebServer.run(options)
                }
            } catch (AssertionViolation e) {
                println(e.message)
            }
        }
        
    }

}

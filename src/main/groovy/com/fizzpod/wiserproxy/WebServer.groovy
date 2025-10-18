package com.fizzpod.wiserproxy;

import com.sun.net.httpserver.HttpServer

import static org.tinylog.Logger.*;

import org.tinylog.*
import com.fizzpod.ibroadcast.functions.*;

import groovy.json.JsonOutput


public class WebServer {

    public static def run(def options) {
        def proxy = new ProxyFunctions(options)
        info("Starting Web Server on port {}", options.port)
        HttpServer.create(new InetSocketAddress(options.port), 0).with {
            createContext("/data") { http ->
                try {
                    // Proxy to IBroadcast functions
                    info("Received {} on {} from {}", http.requestMethod, http.requestURI, http.remoteAddress.hostName)
                    if("GET" == http.requestMethod) {
                        proxy.doGet(http)
                    } else if("POST" == http.requestMethod) {
                        proxy.doPost(http)
                    } else {
                        throw new RuntimeException("Unsupported method: ${http.requestMethod}")
                    }
                } catch( Exception e) {
                    error("Error processing request: {}", e.message)
                    error(e)
                    http.responseHeaders.add("Content-type", "application/json")
                    http.sendResponseHeaders(500, 0)
                    http.responseBody.withWriter { out ->
                        def errorResponse = [
                            status: "error",
                            message: "server error, unable to process request"
                        ]
                        out << JsonOutput.toJson(errorResponse)
                    }
                }
            }
            createContext("/hello") { http ->


                http.responseHeaders.add("Content-type", "text/plain")
                http.sendResponseHeaders(200, 0)
                http.responseBody.withWriter { out ->
                    out << "Hello ${http.remoteAddress.hostName}!"
                }
            }
            createContext("/status") { http ->
                http.responseHeaders.add("Content-type", "application/json")
                http.sendResponseHeaders(200, 0)
                http.responseBody.withWriter { out ->
                    def status = [
                        status: "ok",
                        timestamp: System.currentTimeMillis()
                    ]
                    out << JsonOutput.toJson(status)
                }
            }
            start()
            //Thread.sleep(5000);
        }
    }

}
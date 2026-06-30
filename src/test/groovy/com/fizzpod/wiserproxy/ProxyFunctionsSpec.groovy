package com.fizzpod.wiserproxy

import spock.lang.Specification
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.Headers as SunHeaders

class ProxyFunctionsSpec extends Specification {

    def "should resolve target url correctly under different configurations"() {
        given:
        def proxy = new ProxyFunctions([:])

        expect:
        proxy.resolveUrl(targetUrl, requestUri) == expectedUrl

        where:
        targetUrl              | requestUri      | expectedUrl
        "wiser.local"          | "/data/domain"  | "http://wiser.local/data/domain"
        "http://wiser.local"   | "/data/domain"  | "http://wiser.local/data/domain"
        "https://wiser.local"  | "/hello"        | "https://wiser.local/hello"
        "wiser.local/"         | "/status"       | "http://wiser.local/status"
        "http://wiser.local/"  | "/status"       | "http://wiser.local/status"
    }

    def "should build headers correctly and filter out hop-by-hop/metadata headers"() {
        given:
        def options = [url: "http://wiser.local", secret: "test-secret"]
        def proxy = new ProxyFunctions(options)

        def requestHeaders = new SunHeaders()
        requestHeaders.put("Connection", ["keep-alive"])
        requestHeaders.put("Host", ["localhost:9080"])
        requestHeaders.put("Content-Length", ["123"])
        requestHeaders.put("Content-Type", ["application/json"])
        requestHeaders.put("Custom-Header", ["value1", "value2"])

        def exchange = [
            getRequestHeaders: { -> requestHeaders }
        ]

        when:
        def headers = proxy.buildHeaders(exchange, "http://wiser.local/data")

        then:
        headers.get("Connection") == null
        headers.get("Content-Length") == null
        headers.get("Host") == "wiser.local"
        headers.get("Secret") == "test-secret"
        headers.get("Content-Type") == "application/json"
        headers.get("Custom-Header") == "value2"
        headers.values("Custom-Header") == ["value1", "value2"]
    }

    def "should build headers correctly without secret when none is provided"() {
        given:
        def options = [url: "http://wiser.local"]
        def proxy = new ProxyFunctions(options)

        def requestHeaders = new SunHeaders()
        def exchange = [
            getRequestHeaders: { -> requestHeaders }
        ]

        when:
        def headers = proxy.buildHeaders(exchange, "http://wiser.local/data")

        then:
        headers.get("Host") == "wiser.local"
        headers.get("Secret") == null
    }
}

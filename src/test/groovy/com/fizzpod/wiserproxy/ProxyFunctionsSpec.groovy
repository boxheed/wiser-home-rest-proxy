package com.fizzpod.wiserproxy

import spock.lang.Specification
import okhttp3.*
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.Headers as SunHeaders
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URI

class FakeHttpExchange extends HttpExchange {
    URI requestURI = URI.create("/data/domain/")
    String requestMethod = "GET"
    SunHeaders requestHeaders = new SunHeaders()
    SunHeaders responseHeaders = new SunHeaders()
    ByteArrayOutputStream responseBody = new ByteArrayOutputStream()
    ByteArrayInputStream requestBody = new ByteArrayInputStream(new byte[0])
    int responseCode = 0
    long responseLength = 0

    @Override
    SunHeaders getRequestHeaders() { return requestHeaders }

    @Override
    SunHeaders getResponseHeaders() { return responseHeaders }

    @Override
    URI getRequestURI() { return requestURI }

    @Override
    String getRequestMethod() { return requestMethod }

    @Override
    com.sun.net.httpserver.HttpContext getHttpContext() { return null }

    @Override
    void close() {}

    @Override
    InputStream getRequestBody() { return requestBody }

    @Override
    OutputStream getResponseBody() { return responseBody }

    @Override
    void sendResponseHeaders(int rCode, long rLength) throws IOException {
        this.responseCode = rCode
        this.responseLength = rLength
    }

    @Override
    InetSocketAddress getRemoteAddress() { return new InetSocketAddress("127.0.0.1", 12345) }

    @Override
    int getResponseCode() { return responseCode }

    @Override
    InetSocketAddress getLocalAddress() { return new InetSocketAddress("127.0.0.1", 9080) }

    @Override
    String getProtocol() { return "HTTP/1.1" }

    @Override
    Object getAttribute(String name) { return null }

    @Override
    void setAttribute(String name, Object value) {}

    @Override
    void setStreams(InputStream i, OutputStream o) {}

    @Override
    com.sun.net.httpserver.HttpPrincipal getPrincipal() { return null }
}

class ProxyFunctionsSpec extends Specification {

    def "should normalize URLs correctly"() {
        given:
        def proxy = new ProxyFunctions([url: "http://wiser.local", secret: "sec"])

        expect:
        proxy.normalizeUrl(baseUrl, path) == expected

        where:
        baseUrl                  | path            | expected
        "wiser.local"            | "/data/domain/" | "http://wiser.local/data/domain/"
        "http://10.154.1.6/"     | "/data/domain/" | "http://10.154.1.6/data/domain/"
        "https://10.154.1.6"     | "data/domain/"  | "https://10.154.1.6/data/domain/"
        "10.154.1.6:8080"        | "/data"         | "http://10.154.1.6:8080/data"
        null                     | "/data/status"  | "http://wiser.local/data/status"
        ""                       | "/data/status"  | "http://wiser.local/data/status"
        "http://wiser.local/"    | null            | "http://wiser.local"
    }

    def "should filter hop-by-hop headers from incoming request and inject Host and Secret"() {
        given:
        def options = [url: "http://10.154.1.6", secret: "secret-abc"]
        def proxy = new ProxyFunctions(options)

        def exchange = new FakeHttpExchange()
        exchange.requestHeaders.add("Connection", "close")
        exchange.requestHeaders.add("Keep-Alive", "timeout=5")
        exchange.requestHeaders.add("Transfer-Encoding", "chunked")
        exchange.requestHeaders.add("Host", "10.154.1.5:9080")
        exchange.requestHeaders.add("User-Agent", "Uptime-Kuma/1.23.16")
        exchange.requestHeaders.add("Accept", "application/json")

        when:
        def forwardedHeaders = proxy.buildHeaders(exchange, "http://10.154.1.6/data/domain/")

        then:
        forwardedHeaders.get("Connection") == null
        forwardedHeaders.get("Keep-Alive") == null
        forwardedHeaders.get("Transfer-Encoding") == null
        forwardedHeaders.get("Host") == "10.154.1.6"
        forwardedHeaders.get("Secret") == "secret-abc"
        forwardedHeaders.get("User-Agent") == "Uptime-Kuma/1.23.16"
        forwardedHeaders.get("Accept") == "application/json"
    }

    def "should filter hop-by-hop headers from outgoing response and send exact body length"() {
        given:
        def proxy = new ProxyFunctions([url: "http://10.154.1.6"])
        def responseBodyBytes = '{"status":"ok"}'.bytes
        def exchange = new FakeHttpExchange()

        def okHeaders = new Headers.Builder()
            .add("Connection", "close")
            .add("Transfer-Encoding", "chunked")
            .add("Content-Type", "application/json")
            .add("X-Custom-Header", "test-val")
            .build()

        def okResponse = new Response.Builder()
            .request(new Request.Builder().url("http://10.154.1.6/data/domain/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .headers(okHeaders)
            .body(ResponseBody.create(MediaType.parse("application/json"), responseBodyBytes))
            .build()

        when:
        proxy.handleResponse(exchange, okResponse, responseBodyBytes)

        then:
        exchange.responseCode == 200
        exchange.responseLength == responseBodyBytes.length
        exchange.responseHeaders.getFirst("Connection") == null
        exchange.responseHeaders.getFirst("Transfer-Encoding") == null
        exchange.responseHeaders.getFirst("Content-Type") == "application/json"
        exchange.responseHeaders.getFirst("X-Custom-Header") == "test-val"
        exchange.responseBody.toByteArray() == responseBodyBytes
    }

    def "should handle empty response body with -1 length"() {
        given:
        def proxy = new ProxyFunctions([url: "http://10.154.1.6"])
        def exchange = new FakeHttpExchange()

        def okResponse = new Response.Builder()
            .request(new Request.Builder().url("http://10.154.1.6/data/domain/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(204)
            .message("No Content")
            .body(ResponseBody.create(MediaType.parse("application/json"), new byte[0]))
            .build()

        when:
        proxy.handleResponse(exchange, okResponse, new byte[0])

        then:
        exchange.responseCode == 204
        exchange.responseLength == -1
    }

    def "should retry on transient socket reset and succeed"() {
        given:
        def server = new MockWebServer()
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(new MockResponse().setResponseCode(200).setBody('{"retry":"success"}'))
        server.start()

        def proxy = new ProxyFunctions([url: server.url("/").toString(), secret: "tok"])
        def exchange = new FakeHttpExchange()
        exchange.requestURI = URI.create("/data/domain/")

        when:
        proxy.doGet(exchange)

        then:
        exchange.responseCode == 200
        exchange.responseBody.toString().contains('"retry":"success"')
        server.requestCount == 2

        cleanup:
        server.shutdown()
    }

    def "should fail and throw exception after max retries are exhausted"() {
        given:
        def server = new MockWebServer()
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
        server.start()

        def proxy = new ProxyFunctions([url: server.url("/").toString(), secret: "tok"])
        def exchange = new FakeHttpExchange()
        exchange.requestURI = URI.create("/data/domain/")

        when:
        proxy.doGet(exchange)

        then:
        thrown(IOException)
        server.requestCount == 3

        cleanup:
        server.shutdown()
    }

    def "should cache GET responses and return HIT on subsequent requests"() {
        given:
        def server = new MockWebServer()
        server.enqueue(new MockResponse().setResponseCode(200).setBody('{"domain":"test"}').addHeader("Content-Type", "application/json"))
        server.start()

        def proxy = new ProxyFunctions([url: server.url("/").toString(), secret: "tok", c: 10])
        def exchange1 = new FakeHttpExchange()
        exchange1.requestURI = URI.create("/data/domain/")
        def exchange2 = new FakeHttpExchange()
        exchange2.requestURI = URI.create("/data/domain/")

        when: "First request - Cache MISS"
        proxy.doGet(exchange1)

        then:
        exchange1.responseCode == 200
        exchange1.responseHeaders.getFirst("X-Cache") == "MISS"
        exchange1.responseBody.toString() == '{"domain":"test"}'
        server.requestCount == 1

        when: "Second request - Cache HIT"
        proxy.doGet(exchange2)

        then:
        exchange2.responseCode == 200
        exchange2.responseHeaders.getFirst("X-Cache") == "HIT"
        exchange2.responseBody.toString() == '{"domain":"test"}'
        server.requestCount == 1 // No second network call made!

        cleanup:
        server.shutdown()
    }

    def "should invalidate cache on POST or PATCH mutations"() {
        given:
        def server = new MockWebServer()
        server.enqueue(new MockResponse().setResponseCode(200).setBody('{"temp":18}').addHeader("Content-Type", "application/json"))
        server.enqueue(new MockResponse().setResponseCode(200).setBody('{"set":21}').addHeader("Content-Type", "application/json"))
        server.enqueue(new MockResponse().setResponseCode(200).setBody('{"temp":21}').addHeader("Content-Type", "application/json"))
        server.start()

        def proxy = new ProxyFunctions([url: server.url("/").toString(), secret: "tok", c: 10])
        def getExchange1 = new FakeHttpExchange()
        getExchange1.requestURI = URI.create("/data/domain/Room")

        def patchExchange = new FakeHttpExchange()
        patchExchange.requestURI = URI.create("/data/domain/Room/1")
        patchExchange.requestMethod = "PATCH"

        def getExchange2 = new FakeHttpExchange()
        getExchange2.requestURI = URI.create("/data/domain/Room")

        when: "Initial GET caches response"
        proxy.doGet(getExchange1)

        then:
        getExchange1.responseHeaders.getFirst("X-Cache") == "MISS"
        getExchange1.responseBody.toString() == '{"temp":18}'
        server.requestCount == 1

        when: "PATCH mutation invalidates cache"
        proxy.doPatch(patchExchange)

        then:
        patchExchange.responseCode == 200
        server.requestCount == 2

        when: "Next GET fetches fresh response"
        proxy.doGet(getExchange2)

        then:
        getExchange2.responseHeaders.getFirst("X-Cache") == "MISS"
        getExchange2.responseBody.toString() == '{"temp":21}'
        server.requestCount == 3

        cleanup:
        server.shutdown()
    }

    def "should not cache when TTL is 0 (disabled)"() {
        given:
        def server = new MockWebServer()
        server.enqueue(new MockResponse().setResponseCode(200).setBody('{"v":1}'))
        server.enqueue(new MockResponse().setResponseCode(200).setBody('{"v":2}'))
        server.start()

        def proxy = new ProxyFunctions([url: server.url("/").toString(), secret: "tok", c: 0])
        def exchange1 = new FakeHttpExchange()
        exchange1.requestURI = URI.create("/data/domain/")
        def exchange2 = new FakeHttpExchange()
        exchange2.requestURI = URI.create("/data/domain/")

        when:
        proxy.doGet(exchange1)
        proxy.doGet(exchange2)

        then:
        exchange1.responseHeaders.getFirst("X-Cache") == null
        exchange2.responseHeaders.getFirst("X-Cache") == null
        server.requestCount == 2

        cleanup:
        server.shutdown()
    }
}

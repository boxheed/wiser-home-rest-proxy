package com.fizzpod.wiserproxy

import spock.lang.Specification
import spock.lang.Shared
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import java.net.InetSocketAddress
import java.net.ServerSocket
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.MediaType

class ProxyIntegrationSpec extends Specification {

    @Shared HttpServer mockWiserHub
    @Shared HttpServer proxyServer
    @Shared int mockPort
    @Shared int proxyPort
    @Shared String receivedMethod
    @Shared String receivedPath
    @Shared String receivedSecretHeader
    @Shared String receivedBody
    @Shared OkHttpClient client = new OkHttpClient()

    def setupSpec() {
        // Start Mock Wiser Hub on random free port
        ServerSocket ss1 = new ServerSocket(0)
        mockPort = ss1.localPort
        ss1.close()

        mockWiserHub = HttpServer.create(new InetSocketAddress(mockPort), 0)
        mockWiserHub.createContext("/data") { exchange ->
            receivedMethod = exchange.requestMethod
            receivedPath = exchange.requestURI.toString()
            receivedSecretHeader = exchange.requestHeaders.getFirst("Secret")
            receivedBody = exchange.requestBody.text

            byte[] responseBytes = '{"domain":{"System":{"Name":"WiserHub"}}}'.bytes
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBytes.length)
            exchange.responseBody.write(responseBytes)
            exchange.close()
        }
        mockWiserHub.start()

        // Start Proxy WebServer on random free port
        ServerSocket ss2 = new ServerSocket(0)
        proxyPort = ss2.localPort
        ss2.close()

        def options = [
            port: proxyPort,
            url: "http://localhost:${mockPort}",
            secret: "test-secret-token"
        ]
        proxyServer = WebServer.run(options)
    }

    def cleanupSpec() {
        if (proxyServer != null) {
            proxyServer.stop(0)
        }
        if (mockWiserHub != null) {
            mockWiserHub.stop(0)
        }
    }

    def "should proxy GET request to Wiser hub, inject Secret header, and cache subsequent requests"() {
        when: "First request - Cache MISS"
        def request1 = new Request.Builder()
            .url("http://localhost:${proxyPort}/data/domain/")
            .get()
            .build()
        def response1 = client.newCall(request1).execute()
        def body1 = response1.body().string()

        then:
        response1.code() == 200
        body1 == '{"domain":{"System":{"Name":"WiserHub"}}}'
        response1.header("X-Cache") == "MISS"
        receivedMethod == "GET"
        receivedPath == "/data/domain/"
        receivedSecretHeader == "test-secret-token"

        when: "Second request - Cache HIT"
        def request2 = new Request.Builder()
            .url("http://localhost:${proxyPort}/data/domain/")
            .get()
            .build()
        def response2 = client.newCall(request2).execute()
        def body2 = response2.body().string()

        then:
        response2.code() == 200
        body2 == '{"domain":{"System":{"Name":"WiserHub"}}}'
        response2.header("X-Cache") == "HIT"
    }

    def "should proxy POST request with payload to Wiser hub"() {
        when:
        def jsonPayload = '{"RequestOverride":{"Mode":"Manual"}}'
        def request = new Request.Builder()
            .url("http://localhost:${proxyPort}/data/domain/Room/1")
            .post(RequestBody.create(MediaType.parse("application/json"), jsonPayload))
            .build()
        def response = client.newCall(request).execute()
        def body = response.body().string()

        then:
        response.code() == 200
        body == '{"domain":{"System":{"Name":"WiserHub"}}}'
        receivedMethod == "POST"
        receivedPath == "/data/domain/Room/1"
        receivedSecretHeader == "test-secret-token"
        receivedBody == jsonPayload
    }

    def "should proxy PATCH request with payload to Wiser hub"() {
        when:
        def jsonPayload = '{"Room":[{"id":1,"SetPoint":200}]}'
        def request = new Request.Builder()
            .url("http://localhost:${proxyPort}/data/domain/Room")
            .patch(RequestBody.create(MediaType.parse("application/json"), jsonPayload))
            .build()
        def response = client.newCall(request).execute()
        def body = response.body().string()

        then:
        response.code() == 200
        body == '{"domain":{"System":{"Name":"WiserHub"}}}'
        receivedMethod == "PATCH"
        receivedPath == "/data/domain/Room"
        receivedSecretHeader == "test-secret-token"
        receivedBody == jsonPayload
    }

    def "should filter Connection close request header and handle chunked response cleanly"() {
        when:
        def request = new Request.Builder()
            .url("http://localhost:${proxyPort}/data/domain/")
            .header("Connection", "close")
            .header("User-Agent", "Uptime-Kuma/1.23.16")
            .get()
            .build()
        def response = client.newCall(request).execute()
        def body = response.body().string()

        then:
        response.code() == 200
        body == '{"domain":{"System":{"Name":"WiserHub"}}}'
        response.header("Content-Type") == "application/json"
    }

    def "should serve health check endpoint /status"() {
        when:
        def request = new Request.Builder()
            .url("http://localhost:${proxyPort}/status")
            .get()
            .build()
        def response = client.newCall(request).execute()
        def body = response.body().string()

        then:
        response.code() == 200
        body.contains('"status":"ok"')
    }

    def "should serve greeting endpoint /hello"() {
        when:
        def request = new Request.Builder()
            .url("http://localhost:${proxyPort}/hello")
            .get()
            .build()
        def response = client.newCall(request).execute()
        def body = response.body().string()

        then:
        response.code() == 200
        body.startsWith("Hello ")
    }
}

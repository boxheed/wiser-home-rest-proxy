package com.fizzpod.wiserproxy;

import static org.tinylog.Logger.*;

import org.tinylog.*

import okhttp3.*

import groovy.json.JsonOutput

import java.net.URLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager
import javax.net.ssl.TrustManager
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLSession
import java.security.cert.CertificateException

public class ProxyFunctions {

    def options
    def okclient = getUnsafeOkHttpClient()

    public ProxyFunctions(def options) {
        this.options = options
    }

    def doGet(def request) {
        def url = "${options.url}${request.requestURI}"
        info("Proxying GET request to {}", url)
        //copy heders
        def requestHeaders = request.getRequestHeaders()
        def forwardedHeaders = new Headers.Builder()
        for(def header: requestHeaders.entrySet()) {
            header.value.each { value ->
                info("Setting header {}: {}", header.key, value)
                forwardedHeaders.add(header.key, value)
            }
        }
        forwardedHeaders.set("Host", new URL(url).getHost())
        forwardedHeaders.set("Secret", options.secret)
        Request okRequest = new Request.Builder()
            .url(url)
            .get()
            .headers(forwardedHeaders.build())
            .build()
        info("Forwarding request to {}", url)
        try(Response okResponse = okclient.newCall(okRequest).execute()) {
            def status = okResponse.code
            info("Received response code: {} from wiser", status)
            //copy headers
            def responseHeaders = okResponse.headers
            for(def headerName : responseHeaders.names()) {
                def headerValues = responseHeaders.values(headerName)
                headerValues.each { value ->
                    info("Setting response header {}: {}", headerName, value)
                    request.responseHeaders.add(headerName, value)
                }
            }
            //copy code
            request.sendResponseHeaders(status, 0)
            //copy body
            def responseBody = okResponse.body.byteStream()
            request.responseBody.withStream { outStream ->
                responseBody.withStream { inStream ->
                    outStream << inStream
                }
            }   
        }
    }

    def doPost(def request) {
        def url = "${options.url}${request.requestURI}"
        info("Proxying POST request to {}", url)
        //copy heders
        def contentType = request.getRequestHeaders().getFirst("Content-Type");
        def requestHeaders = request.getRequestHeaders()
        def forwardedHeaders = new Headers.Builder()
        for(def header: requestHeaders.entrySet()) {
            header.value.each { value ->
                info("Setting header {}: {}", header.key, value)
                forwardedHeaders.add(header.key, value)
            }
        }
        forwardedHeaders.set("Host", new URL(url).getHost())
        forwardedHeaders.set("Secret", options.secret)
        Request okRequest = new Request.Builder()
            .url(url)
            .post(RequestBody.create(MediaType.parse(contentType), request.requestBody.bytes))
            .headers(forwardedHeaders.build())
            .build()
        info("Forwarding request to {}", url)
        try(Response okResponse = okclient.newCall(okRequest).execute()) {
            def status = okResponse.code
            info("Received response code: {} from wiser", status)
            //copy headers
            def responseHeaders = okResponse.headers
            for(def headerName : responseHeaders.names()) {
                def headerValues = responseHeaders.values(headerName)
                headerValues.each { value ->
                    info("Setting response header {}: {}", headerName, value)
                    request.responseHeaders.add(headerName, value)
                }
            }
            //copy code
            request.sendResponseHeaders(status, 0)
            //copy body
            def responseBody = okResponse.body.byteStream()
            request.responseBody.withStream { outStream ->
                responseBody.withStream { inStream ->
                    outStream << inStream
                }
            }   
        }
    }

    def doPatch(def request) {
        def url = "${options.url}${request.requestURI}"
        info("Proxying Patch request to {}", url)
        //copy heders
        def contentType = request.getRequestHeaders().getFirst("Content-Type");
        def requestHeaders = request.getRequestHeaders()
        def forwardedHeaders = new Headers.Builder()
        for(def header: requestHeaders.entrySet()) {
            header.value.each { value ->
                info("Setting header {}: {}", header.key, value)
                forwardedHeaders.add(header.key, value)
            }
        }
        forwardedHeaders.set("Host", new URL(url).getHost())
        forwardedHeaders.set("Secret", options.secret)
        Request okRequest = new Request.Builder()
            .url(url)
            .patch(RequestBody.create(MediaType.parse(contentType), request.requestBody.bytes))
            .headers(forwardedHeaders.build())
            .build()
        info("Forwarding request to {}", url)
        try(Response okResponse = okclient.newCall(okRequest).execute()) {
            def status = okResponse.code
            info("Received response code: {} from wiser", status)
            //copy headers
            def responseHeaders = okResponse.headers
            for(def headerName : responseHeaders.names()) {
                def headerValues = responseHeaders.values(headerName)
                headerValues.each { value ->
                    info("Setting response header {}: {}", headerName, value)
                    request.responseHeaders.add(headerName, value)
                }
            }
            //copy code
            request.sendResponseHeaders(status, 0)
            //copy body
            def responseBody = okResponse.body.byteStream()
            request.responseBody.withStream { outStream ->
                responseBody.withStream { inStream ->
                    outStream << inStream
                }
            }   
        }
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            // Create a trust manager that does not validate certificate chains
            final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {
                }

                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[]{};
                }
                }
            };

            // Install the all-trusting trust manager
            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            // Create an ssl socket factory with our all-trusting manager
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager)trustAllCerts[0]);
            builder.hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
            });

            OkHttpClient okHttpClient = builder.build();
            return okHttpClient;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
package com.fizzpod.wiserproxy;

import static org.tinylog.Logger.*;

import okhttp3.*;

import java.net.URL;
import javax.net.ssl.*;
import java.security.cert.CertificateException;

public class ProxyFunctions {

    def options;
    def okclient = getUnsafeOkHttpClient();

    public ProxyFunctions(def options) {
        this.options = options;
    }

    def doGet(def request) {
        handleRequest(request, "GET", null);
    }

    def doPost(def request) {
        handleRequest(request, "POST", request.requestBody.bytes);
    }

    def doPatch(def request) {
        handleRequest(request, "PATCH", request.requestBody.bytes);
    }

    private void handleRequest(def request, String method, byte[] body) {
        def url = "${options.url}${request.requestURI}";
        info("Proxying {} request to {}", method, url);

        def forwardedHeaders = buildHeaders(request, url);
        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .headers(forwardedHeaders);

        if (method == "POST" || method == "PATCH") {
            def contentType = request.getRequestHeaders().getFirst("Content-Type");
            requestBuilder.method(method, RequestBody.create(MediaType.parse(contentType), body));
        } else {
            requestBuilder.method(method, null);
        }

        try (Response okResponse = okclient.newCall(requestBuilder.build()).execute()) {
            handleResponse(request, okResponse);
        }
    }

    private Headers buildHeaders(def request, String url) {
        def requestHeaders = request.getRequestHeaders();
        def forwardedHeaders = new Headers.Builder();

        for (def header : requestHeaders.entrySet()) {
            header.value.each { value ->
                info("Setting header {}: {}", header.key, value);
                forwardedHeaders.add(header.key, value);
            }
        }

        forwardedHeaders.set("Host", new URL(url).getHost());
        forwardedHeaders.set("Secret", options.secret);
        return forwardedHeaders.build();
    }

    private void handleResponse(def request, Response okResponse) {
        def status = okResponse.code;
        info("Received response code: {} from wiser", status);

        def responseHeaders = okResponse.headers;
        for (def headerName : responseHeaders.names()) {
            def headerValues = responseHeaders.values(headerName);
            headerValues.each { value ->
                info("Setting response header {}: {}", headerName, value);
                request.responseHeaders.add(headerName, value);
            }
        }

        request.sendResponseHeaders(status, 0);

        def responseBody = okResponse.body.byteStream();
        request.responseBody.withStream { outStream ->
            responseBody.withStream { inStream ->
                outStream << inStream;
            }
        };
    }

    private static OkHttpClient getUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {}

                    @Override
                    public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) throws CertificateException {}

                    @Override
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                        return new java.security.cert.X509Certificate[]{};
                    }
                }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
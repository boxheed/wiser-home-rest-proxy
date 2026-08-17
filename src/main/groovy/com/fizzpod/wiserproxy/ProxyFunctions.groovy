package com.fizzpod.wiserproxy;

import static org.tinylog.Logger.*;

import okhttp3.*;

import java.net.URL;
import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.io.IOException;

public class ProxyFunctions {

    private static final Set<String> HOP_BY_HOP_HEADERS = [
        "connection",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
        "trailers",
        "transfer-encoding",
        "upgrade",
        "host",
        "content-length",
        "secret"
    ] as Set;

    private static final Set<String> HOP_BY_HOP_RESPONSE_HEADERS = [
        "connection",
        "keep-alive",
        "proxy-authenticate",
        "proxy-authorization",
        "te",
        "trailer",
        "trailers",
        "transfer-encoding",
        "upgrade",
        "content-length"
    ] as Set;

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 200;

    def options;
    def okclient;

    public ProxyFunctions(def options) {
        this.options = options;
        this.okclient = getUnsafeOkHttpClient();
    }

    public ProxyFunctions(def options, OkHttpClient okclient) {
        this.options = options;
        this.okclient = okclient;
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

    protected String normalizeUrl(String baseUrl, String path) {
        String base = (baseUrl != null && !baseUrl.trim().isEmpty()) ? baseUrl.trim() : "http://wiser.local";
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (path == null) {
            path = "";
        }
        if (!path.isEmpty() && !path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }

    private void handleRequest(def request, String method, byte[] body) {
        def url = normalizeUrl(options?.url as String, request.requestURI as String);
        info("Proxying {} request to {}", method, url);

        def forwardedHeaders = buildHeaders(request, url);
        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .headers(forwardedHeaders);

        if (method == "POST" || method == "PATCH") {
            def contentType = request.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || contentType.trim().isEmpty()) {
                contentType = "application/json";
            }
            requestBuilder.method(method, RequestBody.create(MediaType.parse(contentType), body != null ? body : new byte[0]));
        } else {
            requestBuilder.get();
        }

        executeWithRetry(request, requestBuilder.build());
    }

    private void executeWithRetry(def request, Request okHttpRequest) {
        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            attempts++;
            Response okResponse = null;
            try {
                okResponse = okclient.newCall(okHttpRequest).execute();
                byte[] responseBytes = okResponse.body() != null ? okResponse.body().bytes() : new byte[0];
                handleResponse(request, okResponse, responseBytes);
                return;
            } catch (IOException e) {
                if (attempts >= MAX_RETRIES) {
                    error("Request to {} failed after {} attempts: {}", okHttpRequest.url(), attempts, e.message);
                    throw e;
                }
                warn("Request to {} failed on attempt {}/{}: {}. Retrying in {}ms...", okHttpRequest.url(), attempts, MAX_RETRIES, e.message, RETRY_DELAY_MS);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            } finally {
                if (okResponse != null) {
                    okResponse.close();
                }
            }
        }
    }

    private Headers buildHeaders(def request, String url) {
        def requestHeaders = request.getRequestHeaders();
        def forwardedHeaders = new Headers.Builder();

        for (def header : requestHeaders.entrySet()) {
            def key = header.key;
            if (key != null && !HOP_BY_HOP_HEADERS.contains(key.toLowerCase())) {
                header.value.each { value ->
                    info("Setting header {}: {}", key, value);
                    forwardedHeaders.add(key, value);
                }
            }
        }

        forwardedHeaders.set("Host", new URL(url).getHost());
        if (options?.secret != null) {
            forwardedHeaders.set("Secret", options.secret);
        }
        return forwardedHeaders.build();
    }

    private void handleResponse(def request, Response okResponse, byte[] responseBytes) {
        def status = okResponse.code;
        info("Received response code: {} from wiser", status);

        def responseHeaders = okResponse.headers;
        for (def headerName : responseHeaders.names()) {
            if (headerName != null && !HOP_BY_HOP_RESPONSE_HEADERS.contains(headerName.toLowerCase())) {
                def headerValues = responseHeaders.values(headerName);
                headerValues.each { value ->
                    info("Setting response header {}: {}", headerName, value);
                    request.responseHeaders.add(headerName, value);
                }
            }
        }

        if (responseBytes != null && responseBytes.length > 0) {
            request.sendResponseHeaders(status, responseBytes.length);
            request.responseBody.withStream { outStream ->
                outStream.write(responseBytes);
            }
        } else {
            request.sendResponseHeaders(status, -1);
            request.responseBody.close();
        }
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
                        return new java.security.cert.X509Certificate[0];
                    }
                }
            };

            final SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            ConnectionSpec compatibleSpec = new ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
                .allEnabledTlsVersions()
                .allEnabledCipherSuites()
                .build();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.connectionSpecs([ConnectionSpec.MODERN_TLS, compatibleSpec, ConnectionSpec.CLEARTEXT]);
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);
            builder.retryOnConnectionFailure(true);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
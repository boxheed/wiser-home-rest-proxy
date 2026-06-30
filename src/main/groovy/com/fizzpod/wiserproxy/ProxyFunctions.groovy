package com.fizzpod.wiserproxy;

import static org.tinylog.Logger.*;

import okhttp3.*;

import java.net.URL;
import javax.net.ssl.*;
import java.security.cert.CertificateException;

public class ProxyFunctions {

    private static final Set<String> IGNORED_HEADERS = [
        "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
        "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length"
    ] as Set;

    private final def options;
    private final OkHttpClient okclient = getUnsafeOkHttpClient();

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
        def url = resolveUrl(options.url, request.requestURI.toString());
        info("Proxying {} request to {}", method, url);

        def forwardedHeaders = buildHeaders(request, url);
        Request.Builder requestBuilder = new Request.Builder()
            .url(url)
            .headers(forwardedHeaders);

        if (method == "POST" || method == "PATCH") {
            def contentType = request.getRequestHeaders().getFirst("Content-Type");
            requestBuilder.method(method, RequestBody.create(MediaType.parse(contentType), body));
        } else {
            requestBuilder.get();
        }

        try (Response okResponse = okclient.newCall(requestBuilder.build()).execute()) {
            handleResponse(request, okResponse);
        }
    }

    String resolveUrl(String targetUrl, String requestUri) {
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "http://" + targetUrl
        }
        if (targetUrl.endsWith("/")) {
            targetUrl = targetUrl.substring(0, targetUrl.length() - 1)
        }
        return "${targetUrl}${requestUri}"
    }

    private Headers buildHeaders(def request, String url) {
        def requestHeaders = request.getRequestHeaders();
        def forwardedHeaders = new Headers.Builder();

        for (def header : requestHeaders.entrySet()) {
            if (IGNORED_HEADERS.contains(header.key.toLowerCase())) {
                continue;
            }
            header.value.each { value ->
                info("Setting header {}: {}", header.key, value);
                forwardedHeaders.add(header.key, value);
            }
        }

        forwardedHeaders.set("Host", new URL(url).getHost());
        if (options.secret) {
            forwardedHeaders.set("Secret", options.secret);
        }
        return forwardedHeaders.build();
    }

    private void handleResponse(def request, Response okResponse) {
        def status = okResponse.code;
        info("Received response code: {} from wiser", status);

        def responseHeaders = okResponse.headers;
        for (def headerName : responseHeaders.names()) {
            if (IGNORED_HEADERS.contains(headerName.toLowerCase())) {
                continue;
            }
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
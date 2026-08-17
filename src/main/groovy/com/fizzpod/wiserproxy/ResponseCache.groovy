package com.fizzpod.wiserproxy

import java.util.concurrent.ConcurrentHashMap
import java.util.Map
import java.util.Collections
import java.util.HashMap
import java.util.ArrayList
import java.util.List

public class ResponseCache {

    public static class CachedResponse {
        final int statusCode
        final Map<String, List<String>> headers
        final byte[] body
        final long expirationTimeMillis

        public CachedResponse(int statusCode, Map<String, List<String>> headers, byte[] body, long ttlSeconds) {
            this.statusCode = statusCode
            this.headers = headers != null ? new HashMap<>(headers) : Collections.emptyMap()
            this.body = body != null ? body.clone() : new byte[0]
            this.expirationTimeMillis = System.currentTimeMillis() + (ttlSeconds * 1000L)
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expirationTimeMillis
        }
    }

    private final long ttlSeconds
    private final ConcurrentHashMap<String, CachedResponse> cache = new ConcurrentHashMap<>()

    public ResponseCache(long ttlSeconds) {
        this.ttlSeconds = ttlSeconds
    }

    public boolean isEnabled() {
        return ttlSeconds > 0
    }

    public long getTtlSeconds() {
        return ttlSeconds
    }

    public CachedResponse get(String key) {
        if (!isEnabled() || key == null) {
            return null
        }
        CachedResponse cached = cache.get(key)
        if (cached == null) {
            return null
        }
        if (cached.isExpired()) {
            cache.remove(key, cached)
            return null
        }
        return cached
    }

    public void put(String key, int statusCode, Map<String, List<String>> headers, byte[] body) {
        if (!isEnabled() || key == null) {
            return
        }
        if (statusCode >= 200 && statusCode < 300) {
            cache.put(key, new CachedResponse(statusCode, headers, body, ttlSeconds))
        }
    }

    public void invalidateAll() {
        if (isEnabled()) {
            cache.clear()
        }
    }

    public void invalidate(String key) {
        if (isEnabled() && key != null) {
            cache.remove(key)
        }
    }

    public int size() {
        return cache.size()
    }
}

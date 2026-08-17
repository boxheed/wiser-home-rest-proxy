package com.fizzpod.wiserproxy

import spock.lang.Specification

class ResponseCacheSpec extends Specification {

    def "should report enabled when ttl > 0"() {
        expect:
        new ResponseCache(5).isEnabled()
        !new ResponseCache(0).isEnabled()
        !new ResponseCache(-1).isEnabled()
    }

    def "should put and get cached response before expiration"() {
        given:
        def cache = new ResponseCache(5)
        def headers = ["Content-Type": ["application/json"]]
        def body = '{"status":"ok"}'.bytes

        when:
        cache.put("/data/domain/", 200, headers, body)
        def cached = cache.get("/data/domain/")

        then:
        cached != null
        cached.statusCode == 200
        cached.headers.get("Content-Type") == ["application/json"]
        cached.body == body
        !cached.isExpired()
    }

    def "should return null when entry is expired"() {
        given:
        def cache = new ResponseCache(1)
        cache.put("/data/domain/", 200, [:], '{"test":1}'.bytes)

        when:
        Thread.sleep(1100)
        def cached = cache.get("/data/domain/")

        then:
        cached == null
        cache.size() == 0
    }

    def "should not cache error responses (non-2xx)"() {
        given:
        def cache = new ResponseCache(5)

        when:
        cache.put("/data/500", 500, [:], 'error'.bytes)
        cache.put("/data/404", 404, [:], 'not found'.bytes)

        then:
        cache.get("/data/500") == null
        cache.get("/data/404") == null
        cache.size() == 0
    }

    def "should invalidate all entries"() {
        given:
        def cache = new ResponseCache(5)
        cache.put("/data/1", 200, [:], '1'.bytes)
        cache.put("/data/2", 200, [:], '2'.bytes)

        when:
        cache.invalidateAll()

        then:
        cache.get("/data/1") == null
        cache.get("/data/2") == null
        cache.size() == 0
    }

    def "should invalidate specific key"() {
        given:
        def cache = new ResponseCache(5)
        cache.put("/data/1", 200, [:], '1'.bytes)
        cache.put("/data/2", 200, [:], '2'.bytes)

        when:
        cache.invalidate("/data/1")

        then:
        cache.get("/data/1") == null
        cache.get("/data/2") != null
        cache.size() == 1
    }

    def "should not store or return anything when cache is disabled"() {
        given:
        def cache = new ResponseCache(0)

        when:
        cache.put("/data/1", 200, [:], '1'.bytes)

        then:
        cache.get("/data/1") == null
        cache.size() == 0
    }
}

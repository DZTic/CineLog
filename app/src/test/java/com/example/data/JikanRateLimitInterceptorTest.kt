package com.example.data

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class JikanRateLimitInterceptorTest {

    private class FakeChain(
        private val request: Request,
        private val responseSupplier: () -> Response
    ) : Interceptor.Chain {
        var proceedCallCount = 0

        override fun request(): Request = request

        override fun proceed(request: Request): Response {
            proceedCallCount++
            return responseSupplier()
        }

        override fun connection(): okhttp3.Connection? = null
        override fun call(): okhttp3.Call = throw NotImplementedError()
        override fun connectTimeoutMillis(): Int = 10000
        override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 10000
        override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 10000
        override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
    }

    private fun createResponse(request: Request, code: Int, headers: Map<String, String> = emptyMap()): Response {
        val builder = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else if (code == 429) "Too Many Requests" else "Error")
            .body("{}".toResponseBody("application/json".toMediaTypeOrNull()))

        headers.forEach { (name, value) ->
            builder.header(name, value)
        }
        return builder.build()
    }

    @Test
    fun `returns success immediately on 200`() {
        val request = Request.Builder().url("https://api.jikan.moe/v4/anime/1").build()
        val sleepDelays = mutableListOf<Long>()
        val interceptor = JikanRateLimitInterceptor(
            maxRetries = 2,
            defaultBackoffMs = 1000L,
            sleeper = { sleepDelays.add(it) }
        )

        val chain = FakeChain(request) {
            createResponse(request, 200)
        }

        val response = interceptor.intercept(chain)
        assertEquals(200, response.code)
        assertEquals(1, chain.proceedCallCount)
        assertTrue(sleepDelays.isEmpty())
    }

    @Test
    fun `retries on 429 and succeeds when next call returns 200`() {
        val request = Request.Builder().url("https://api.jikan.moe/v4/anime/1").build()
        val sleepDelays = mutableListOf<Long>()
        val interceptor = JikanRateLimitInterceptor(
            maxRetries = 2,
            defaultBackoffMs = 1000L,
            sleeper = { sleepDelays.add(it) }
        )

        var calls = 0
        val chain = FakeChain(request) {
            calls++
            if (calls == 1) {
                createResponse(request, 429)
            } else {
                createResponse(request, 200)
            }
        }

        val response = interceptor.intercept(chain)
        assertEquals(200, response.code)
        assertEquals(2, chain.proceedCallCount)
        assertEquals(listOf(1000L), sleepDelays)
    }

    @Test
    fun `respects Retry-After header on 429`() {
        val request = Request.Builder().url("https://api.jikan.moe/v4/anime/1").build()
        val sleepDelays = mutableListOf<Long>()
        val interceptor = JikanRateLimitInterceptor(
            maxRetries = 2,
            defaultBackoffMs = 1000L,
            sleeper = { sleepDelays.add(it) }
        )

        var calls = 0
        val chain = FakeChain(request) {
            calls++
            if (calls == 1) {
                createResponse(request, 429, mapOf("Retry-After" to "3"))
            } else {
                createResponse(request, 200)
            }
        }

        val response = interceptor.intercept(chain)
        assertEquals(200, response.code)
        assertEquals(2, chain.proceedCallCount)
        assertEquals(listOf(3000L), sleepDelays)
    }

    @Test
    fun `stops retrying and returns 429 when max retries exceeded`() {
        val request = Request.Builder().url("https://api.jikan.moe/v4/anime/1").build()
        val sleepDelays = mutableListOf<Long>()
        val interceptor = JikanRateLimitInterceptor(
            maxRetries = 2,
            defaultBackoffMs = 1000L,
            sleeper = { sleepDelays.add(it) }
        )

        val chain = FakeChain(request) {
            createResponse(request, 429)
        }

        val response = interceptor.intercept(chain)
        assertEquals(429, response.code)
        assertEquals(3, chain.proceedCallCount) // 1 initial + 2 retries
        assertEquals(listOf(1000L, 2000L), sleepDelays)
    }

    @Test
    fun `does not retry for other error status codes like 404 or 500`() {
        val request = Request.Builder().url("https://api.jikan.moe/v4/anime/1").build()
        val sleepDelays = mutableListOf<Long>()
        val interceptor = JikanRateLimitInterceptor(
            maxRetries = 2,
            defaultBackoffMs = 1000L,
            sleeper = { sleepDelays.add(it) }
        )

        val chain = FakeChain(request) {
            createResponse(request, 404)
        }

        val response = interceptor.intercept(chain)
        assertEquals(404, response.code)
        assertEquals(1, chain.proceedCallCount)
        assertTrue(sleepDelays.isEmpty())
    }
}

package com.example.data

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Interceptor OkHttp dédié pour l'API Jikan.
 * Détecte les réponses HTTP 429 (Too Many Requests), respecte l'en-tête `Retry-After`
 * ou applique un backoff linéaire/exponentiel borné (1 à 2 tentatives de retry).
 */
class JikanRateLimitInterceptor(
    private val maxRetries: Int = 2,
    private val defaultBackoffMs: Long = 1000L,
    private val sleeper: (Long) -> Unit = { Thread.sleep(it) }
) : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var response = chain.proceed(request)
        var attempt = 0

        while (response.code == 429 && attempt < maxRetries) {
            attempt++
            val retryAfterHeader = response.header("Retry-After")
            val retryAfterSeconds = retryAfterHeader?.toLongOrNull()
            val delayMs = if (retryAfterSeconds != null && retryAfterSeconds > 0) {
                (retryAfterSeconds * 1000L).coerceIn(100L, 5000L)
            } else {
                (attempt * defaultBackoffMs).coerceIn(100L, 5000L)
            }

            response.close()

            try {
                sleeper(delayMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Interrupted during Jikan rate-limit backoff", e)
            }

            response = chain.proceed(request)
        }

        return response
    }
}

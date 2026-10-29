package cz.tesar.bp

import java.io.IOException
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration

object Http {
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun send(request: HttpRequest, attempts: Int = 5): HttpResponse<String> {
        var attempt = 0
        while (true) {
            attempt++
            val response = try {
                client.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (e: IOException) {
                if (attempt >= attempts) throw e
                Thread.sleep(backoff(attempt))
                continue
            }
            val status = response.statusCode()
            val retryable = status == 429 || status >= 500
            if (!retryable || attempt >= attempts) return response
            val retryAfter = response.headers().firstValue("Retry-After").orElse(null)?.toLongOrNull()
            Thread.sleep(retryAfter?.times(1000) ?: backoff(attempt))
        }
    }

    fun form(vararg params: Pair<String, String>): HttpRequest.BodyPublisher =
        HttpRequest.BodyPublishers.ofString(
            params.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }
        )

    fun encode(value: String): String = URLEncoder.encode(value, UTF_8)

    private fun backoff(attempt: Int): Long = 1000L * (1L shl (attempt - 1)).coerceAtMost(30)
}

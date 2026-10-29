package cz.tesar.bp

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class SpotifyAuth(
    private val clientId: String,
    private val redirectUri: String,
    private val tokenFile: Path = Path.of(".spotify-token.json"),
) {
    private val scopes = "playlist-read-private playlist-read-collaborative"
    private var accessToken: String? = null
    private var expiresAt: Instant = Instant.EPOCH

    fun token(): String {
        val current = accessToken
        if (current != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) return current
        val stored = storedRefreshToken()
        val response = stored?.let { refresh(it) } ?: authorize()
        accept(response, stored)
        return accessToken!!
    }

    private fun authorize(): Map<String, String> {
        val verifier = randomString(64)
        val challenge = base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(UTF_8)))
        val state = randomString(16)
        val redirect = URI(redirectUri)
        val url = "https://accounts.spotify.com/authorize" +
            "?client_id=${Http.encode(clientId)}" +
            "&response_type=code" +
            "&redirect_uri=${Http.encode(redirectUri)}" +
            "&code_challenge_method=S256" +
            "&code_challenge=$challenge" +
            "&state=$state" +
            "&scope=${Http.encode(scopes)}"

        val code = CompletableFuture<String>()
        val server = HttpServer.create(InetSocketAddress(redirect.host, redirect.port), 0)
        server.createContext(redirect.path) { exchange ->
            val params = query(exchange.requestURI.rawQuery)
            val ok = params["state"] == state && params["code"] != null
            val body = if (ok) "Login successful, you can close this window." else "Login failed: ${params["error"] ?: "invalid response"}"
            val bytes = body.toByteArray(UTF_8)
            exchange.responseHeaders.add("Content-Type", "text/plain; charset=utf-8")
            exchange.sendResponseHeaders(if (ok) 200 else 400, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
            if (ok) code.complete(params["code"]) else code.completeExceptionally(IllegalStateException(body))
        }
        server.start()
        try {
            println("Open this URL in a browser and log in to Spotify:")
            println(url)
            runCatching {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(URI(url))
                }
            }
            val authCode = code.get(5, TimeUnit.MINUTES)
            return tokenRequest(
                "grant_type" to "authorization_code",
                "code" to authCode,
                "redirect_uri" to redirectUri,
                "client_id" to clientId,
                "code_verifier" to verifier,
            ) ?: error("Spotify rejected the authorization code exchange.")
        } finally {
            server.stop(0)
        }
    }

    private fun refresh(refreshToken: String): Map<String, String>? = tokenRequest(
        "grant_type" to "refresh_token",
        "refresh_token" to refreshToken,
        "client_id" to clientId,
    )

    private fun tokenRequest(vararg params: Pair<String, String>): Map<String, String>? {
        val request = HttpRequest.newBuilder(URI("https://accounts.spotify.com/api/token"))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(Http.form(*params))
            .build()
        val response = Http.send(request)
        if (response.statusCode() != 200) {
            println("Spotify token ${response.statusCode()}: ${response.body().take(300)}")
            return null
        }
        val json = parseObject(response.body())
        return listOf("access_token", "refresh_token", "expires_in")
            .mapNotNull { key -> json.str(key)?.let { key to it } }
            .toMap()
    }

    private fun accept(response: Map<String, String>, previousRefresh: String?) {
        accessToken = response["access_token"] ?: error("Spotify response does not contain access_token.")
        expiresAt = Instant.now().plusSeconds(response["expires_in"]?.toLongOrNull() ?: 3600)
        val refreshToken = response["refresh_token"] ?: previousRefresh ?: return
        Files.writeString(tokenFile, buildJsonObject { put("refresh_token", refreshToken) }.toString(), UTF_8)
    }

    private fun storedRefreshToken(): String? =
        if (Files.notExists(tokenFile)) null
        else runCatching { parseObject(Files.readString(tokenFile, UTF_8)).str("refresh_token") }.getOrNull()

    private fun query(raw: String?): Map<String, String> =
        raw.orEmpty().split("&").filter { "=" in it }.associate {
            val (k, v) = it.split("=", limit = 2)
            URLDecoder.decode(k, UTF_8) to URLDecoder.decode(v, UTF_8)
        }

    private fun randomString(length: Int): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val random = SecureRandom()
        return (1..length).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
    }

    private fun base64Url(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

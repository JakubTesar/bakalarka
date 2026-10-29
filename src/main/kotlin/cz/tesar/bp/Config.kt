package cz.tesar.bp

import java.nio.file.Path

data class Config(
    val spotifyClientId: String?,
    val spotifyRedirectUri: String,
    val dataDir: Path,
    val ytSongs: Int,
    val ytVideos: Int,
    val ytDelayMs: Long,
    val ytClientVersion: String,
    val ytLanguage: String,
    val ytRegion: String,
) {
    companion object {
        fun load() = Config(
            spotifyClientId = env("SPOTIFY_CLIENT_ID"),
            spotifyRedirectUri = env("SPOTIFY_REDIRECT_URI") ?: "http://127.0.0.1:8888/callback",
            dataDir = Path.of(env("DATA_DIR") ?: "data/raw"),
            ytSongs = env("YTM_SONGS")?.toIntOrNull() ?: 3,
            ytVideos = env("YTM_VIDEOS")?.toIntOrNull() ?: 2,
            ytDelayMs = env("YTM_DELAY_MS")?.toLongOrNull() ?: 1000,
            ytClientVersion = env("YTM_CLIENT_VERSION") ?: "1.20251015.03.00",
            ytLanguage = env("YTM_LANGUAGE") ?: "en",
            ytRegion = env("YTM_REGION") ?: "CZ",
        )

        private fun env(name: String): String? = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
    }
}

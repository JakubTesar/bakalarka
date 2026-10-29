package cz.tesar.bp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URI
import java.net.http.HttpRequest
import java.time.Instant

class SpotifyCollector(private val config: Config) {
    private val output = config.dataDir.resolve("spotify.jsonl")
    private val auth by lazy {
        val clientId = requireNotNull(config.spotifyClientId) { "SPOTIFY_CLIENT_ID is missing in .env." }
        SpotifyAuth(clientId, config.spotifyRedirectUri)
    }

    fun collect(playlists: List<String>) {
        require(playlists.isNotEmpty()) { "Usage: spotify <playlist URL or ID>..." }
        val known = Jsonl.read(output).mapNotNull { it.obj("track")?.str("id") }.toMutableSet()
        val before = known.size

        for (playlist in playlists) {
            val playlistId = playlistId(playlist)
            var next: String? = "https://api.spotify.com/v1/playlists/$playlistId/items?limit=50&additional_types=track"
            var added = 0
            var skipped = 0
            while (next != null) {
                val page = get(next)
                val batch = mutableListOf<JsonObject>()
                for (entry in page.arr("items")) {
                    val track = entry.obj("item") ?: entry.obj("track")
                    val id = track?.str("id")
                    if (track == null || id == null || track.str("type") != "track" || track.bool("is_local") == true || !known.add(id)) {
                        skipped++
                        continue
                    }
                    batch += buildJsonObject {
                        put("playlist_id", playlistId)
                        put("fetched_at", Instant.now().toString())
                        put("track", track)
                    }
                }
                Jsonl.append(output, batch)
                added += batch.size
                next = page.str("next")
            }
            println("Playlist $playlistId: added $added, skipped $skipped")
        }
        println("New tracks: ${known.size - before}, total: ${known.size} -> $output")
    }

    private fun get(url: String): JsonObject {
        val request = HttpRequest.newBuilder(URI(url))
            .header("Authorization", "Bearer ${auth.token()}")
            .GET()
            .build()
        val response = Http.send(request)
        check(response.statusCode() == 200) { "Spotify ${response.statusCode()}: ${response.body().take(500)}" }
        return parseObject(response.body())
    }

    private fun playlistId(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("spotify:playlist:") -> trimmed.removePrefix("spotify:playlist:")
            "/playlist/" in trimmed -> trimmed.substringAfter("/playlist/").substringBefore("?").substringBefore("/")
            else -> trimmed
        }
    }
}

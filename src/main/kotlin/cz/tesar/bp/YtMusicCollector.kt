package cz.tesar.bp

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.http.HttpRequest
import java.time.Instant

class YtMusicCollector(private val config: Config) {
    private val input = config.dataDir.resolve("spotify.jsonl")
    private val output = config.dataDir.resolve("ytmusic.jsonl")
    private val progress = config.dataDir.resolve("ytmusic.done")
    private val endpoint = URI("https://music.youtube.com/youtubei/v1/search?prettyPrint=false")
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
    private val keptRendererKeys = listOf("flexColumns", "fixedColumns", "playlistItemData", "badges")

    enum class Filter(val label: String, val expectedTypes: Set<String>, val paramCandidates: List<String>) {
        SONGS(
            "songs",
            setOf("MUSIC_VIDEO_TYPE_ATV"),
            listOf("EgWKAQIIAWoMEA4QChADEAQQCRAF", "EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D", "EgWKAQIIAWoKEAkQAxAEEAoQBQ%3D%3D"),
        ),
        VIDEOS(
            "videos",
            setOf("MUSIC_VIDEO_TYPE_OMV", "MUSIC_VIDEO_TYPE_UGC", "MUSIC_VIDEO_TYPE_OFFICIAL_SOURCE_MUSIC"),
            listOf("EgWKAQIQAWoMEA4QChADEAQQCRAF", "EgWKAQIQAWoKEAkQBRAKEAMQBA%3D%3D", "EgWKAQIQAWoKEAkQAxAEEAoQBQ%3D%3D"),
        ),
    }

    data class Candidate(val videoId: String, val videoType: String, val renderer: JsonObject)

    private val resolvedParams = mutableMapOf<Filter, String>()

    fun collect() {
        val tracks = Jsonl.read(input).mapNotNull { it.obj("track") }.distinctBy { it.str("id") }
        require(tracks.isNotEmpty()) { "File $input is empty, run the spotify command first." }
        val done = Jsonl.readLines(progress).toMutableSet()
        val todo = tracks.filter { it.str("id") !in done }
        println("Tracks: ${tracks.size}, done: ${tracks.size - todo.size}, remaining: ${todo.size}")

        var failed = 0
        todo.forEachIndexed { index, track ->
            val spotifyId = track.str("id")!!
            val query = query(track)
            val prefix = "[${index + 1}/${todo.size}] $query"
            try {
                val fetchedAt = Instant.now().toString()
                val songs = search(query, Filter.SONGS).take(config.ytSongs)
                Thread.sleep(config.ytDelayMs)
                val videos = search(query, Filter.VIDEOS).take(config.ytVideos)
                val ranked = songs.mapIndexed { i, c -> Triple(Filter.SONGS, i + 1, c) } +
                    videos.mapIndexed { i, c -> Triple(Filter.VIDEOS, i + 1, c) }
                val records = ranked
                    .distinctBy { it.third.videoId }
                    .map { (filter, rank, candidate) ->
                        buildJsonObject {
                            put("spotify_track_id", spotifyId)
                            put("query", query)
                            put("search_filter", filter.label)
                            put("rank", rank)
                            put("fetched_at", fetchedAt)
                            put("video_id", candidate.videoId)
                            put("music_video_type", candidate.videoType)
                            put("renderer", candidate.renderer)
                        }
                    }
                Jsonl.append(output, records)
                Jsonl.appendLine(progress, spotifyId)
                println("$prefix -> ${songs.size} songs, ${videos.size} videos")
            } catch (e: Exception) {
                failed++
                println("$prefix -> error: ${e.message}")
            }
            Thread.sleep(config.ytDelayMs)
        }
        println("Done, errors: $failed -> $output")
    }

    private fun query(track: JsonObject): String {
        val artist = track.arr("artists").firstOrNull()?.str("name").orEmpty()
        return "$artist ${track.str("name").orEmpty()}".trim()
    }

    private fun search(query: String, filter: Filter): List<Candidate> {
        resolvedParams[filter]?.let { return request(query, it) }
        for (params in filter.paramCandidates) {
            val candidates = request(query, params)
            val matching = candidates.count { it.videoType in filter.expectedTypes }
            if (candidates.isNotEmpty() && matching * 2 >= candidates.size) {
                resolvedParams[filter] = params
                println("Filter ${filter.label}: params $params")
                return candidates
            }
        }
        error("No variant of the ${filter.label} filter returned the expected results.")
    }

    private fun request(query: String, params: String): List<Candidate> {
        val body = buildJsonObject {
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", config.ytClientVersion)
                    put("hl", config.ytLanguage)
                    put("gl", config.ytRegion)
                }
            }
            put("query", query)
            put("params", params)
        }
        val request = HttpRequest.newBuilder(endpoint)
            .header("Content-Type", "application/json")
            .header("Origin", "https://music.youtube.com")
            .header("Referer", "https://music.youtube.com/")
            .header("User-Agent", userAgent)
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build()
        val response = Http.send(request)
        check(response.statusCode() == 200) { "YouTube Music ${response.statusCode()}: ${response.body().take(300)}" }
        return parseCandidates(parseObject(response.body()))
    }

    private fun parseCandidates(root: JsonObject): List<Candidate> =
        root.collectObjects("musicResponsiveListItemRenderer")
            .mapNotNull { renderer ->
                val videoId = renderer.obj("playlistItemData")?.str("videoId") ?: renderer.findString("videoId")
                val videoType = renderer.findString("musicVideoType")
                if (videoId == null || videoType == null || !isMusic(videoType)) return@mapNotNull null
                Candidate(videoId, videoType, JsonObject(renderer.filterKeys { it in keptRendererKeys }))
            }
            .distinctBy { it.videoId }

    private fun isMusic(videoType: String) =
        videoType.startsWith("MUSIC_VIDEO_TYPE_") && videoType != "MUSIC_VIDEO_TYPE_PODCAST_EPISODE"
}

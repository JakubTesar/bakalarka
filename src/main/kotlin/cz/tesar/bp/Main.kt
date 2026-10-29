package cz.tesar.bp

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val command = args.firstOrNull() ?: "check"
    val rest = args.drop(1)
    try {
        when (command) {
            "check" -> DbCheck.run()
            "spotify" -> SpotifyCollector(Config.load()).collect(rest)
            "ytmusic" -> YtMusicCollector(Config.load()).collect()
            else -> {
                println("Usage: check | spotify <playlist>... | ytmusic")
                exitProcess(1)
            }
        }
    } catch (e: IllegalArgumentException) {
        println(e.message)
        exitProcess(1)
    } catch (e: IllegalStateException) {
        println(e.message)
        exitProcess(1)
    }
}

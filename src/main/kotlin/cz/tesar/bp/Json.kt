package cz.tesar.bp

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.json.jsonObject
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE

fun parseObject(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): List<JsonObject> = (this[key] as? JsonArray)?.filterIsInstance<JsonObject>() ?: emptyList()

fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

fun JsonElement.findString(key: String): String? = when (this) {
    is JsonObject -> str(key) ?: values.firstNotNullOfOrNull { it.findString(key) }
    is JsonArray -> firstNotNullOfOrNull { it.findString(key) }
    else -> null
}

fun JsonElement.collectObjects(key: String, into: MutableList<JsonObject> = mutableListOf()): MutableList<JsonObject> {
    when (this) {
        is JsonObject -> forEach { (k, v) ->
            if (k == key && v is JsonObject) into += v else v.collectObjects(key, into)
        }
        is JsonArray -> forEach { it.collectObjects(key, into) }
        else -> Unit
    }
    return into
}

object Jsonl {
    @OptIn(ExperimentalSerializationApi::class)
    fun read(file: Path): List<JsonObject> =
        if (Files.notExists(file)) emptyList()
        else Files.newInputStream(file).use { stream ->
            Json.decodeToSequence(stream, JsonElement.serializer(), DecodeSequenceMode.WHITESPACE_SEPARATED)
                .filterIsInstance<JsonObject>()
                .toList()
        }

    fun append(file: Path, records: List<JsonObject>) {
        if (records.isEmpty()) return
        file.parent?.let { Files.createDirectories(it) }
        Files.newBufferedWriter(file, UTF_8, CREATE, APPEND).use { writer ->
            records.forEach {
                writer.write(it.toString())
                writer.newLine()
            }
        }
    }

    fun readLines(file: Path): Set<String> =
        if (Files.notExists(file)) emptySet()
        else Files.readAllLines(file, UTF_8).map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun appendLine(file: Path, line: String) {
        file.parent?.let { Files.createDirectories(it) }
        Files.writeString(file, line + System.lineSeparator(), UTF_8, CREATE, APPEND)
    }
}

package io.github.paulgriffith.kindling.thread.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedReader
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream

@Serializable
data class ThreadDump(
    val version: String,
    val threads: List<Thread>
) {
    companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        @OptIn(ExperimentalSerializationApi::class)
        fun fromJson(path: Path): ThreadDump = JSON.decodeFromStream(path.inputStream())
        fun fromString(path: Path): ThreadDump = fromInputStream(path.inputStream())

        internal fun fromInputStream(stream: InputStream): ThreadDump {
            return stream.bufferedReader().use { reader ->
                val firstLine = reader.readLine()
                if (firstLine.contains(":")) {
                    parseScript(firstLine, reader)
                } else {
                    parseWebPage(firstLine, reader)
                }
            }
        }

        // TODO these two functions are very similar, the logic could potentially be combined
        private fun parseScript(firstLine: String, reader: BufferedReader): ThreadDump {
            val version: String = firstLine.split("Ignition version: ")[1]
            reader.readLine()

            val threads = mutableListOf<Thread>()
            val buffer = mutableListOf<String>()
            reader.forEachLine { line ->
                if (line.startsWith('\"') && buffer.isNotEmpty()) {
                    threads += Thread.fromScript(buffer)
                    buffer.clear()
                }
                buffer += line.trim()
            }
            threads += Thread.fromScript(buffer)

            return ThreadDump(version, threads)
        }

        private fun parseWebPage(firstLine: String, reader: BufferedReader): ThreadDump {
            val version: String = firstLine.split("Ignition v")[1]
            reader.readLine()

            val threads = mutableListOf<Thread>()
            val buffer = mutableListOf<String>()
            reader.forEachLine { line ->
                if ((line.startsWith("Thread") || line.startsWith("Daemon")) && buffer.isNotEmpty()) {
                    threads += Thread.fromWeb(buffer)
                    buffer.clear()
                }
                buffer += line.replace("\t", "")
            }
            threads += Thread.fromWeb(buffer)

            return ThreadDump(version, threads)
        }
    }
}

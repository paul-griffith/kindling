package io.github.paulgriffith.kindling.thread.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedReader
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import java.util.regex.Matcher
import java.util.regex.Pattern

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
            stream.bufferedReader().use { reader ->
                val firstLine = reader.readLine()
                val threads: List<Thread>
                val version = Pattern.compile("[78]\\.\\d\\.\\d\\d?.*").matcher(firstLine).run {
                    find()
                    group()
                }

                reader.readLine()

                if (firstLine.contains(":")) {
                    threads = parseScript(reader)
                } else {
                    return parseWebPage(firstLine, reader)
                }
                return ThreadDump(version, threads)

            }
        }

        // TODO these two functions are very similar, the logic could potentially be combined
        private fun parseScript(reader: BufferedReader): List<Thread> {
            val scriptThreadRegex = "\"(.*)\"" +
                                    "[\\s\r\n]*" +
                                    "CPU:\\s(\\d{1,3}\\.\\d{2})%" +
                                    "[\\s\r\n]*" +
                                    "java\\.lang\\.Thread\\.State:\\s(\\w+_?\\w+)" +
                                    "\r\n" +
                                    "(\\s{6}[\\S\\s]+?)\r\n\r\n"
            val matcher: Matcher = Pattern.compile(scriptThreadRegex).matcher(reader.readText())
            val threads = buildList<Thread> {
                while (matcher.find()) {
                    add(
                        Thread(
                            id = 0,
                            name = matcher.group(1),
                            cpuUsage = matcher.group(2).toDouble(),
                            state = java.lang.Thread.State.valueOf(matcher.group(3)),
                            isDaemon = false,
                            stacktrace = matcher.group(4).split("\n").map { line -> line.trim() }
                        )
                    )
                }
            }
            return threads
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

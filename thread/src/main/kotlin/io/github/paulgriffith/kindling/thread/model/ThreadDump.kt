package io.github.paulgriffith.kindling.thread.model

import io.github.paulgriffith.kindling.utils.getValue
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.BufferedReader
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
import java.lang.Thread.State as ThreadState

@Serializable
data class ThreadDump(
    val version: String,
    val threads: List<Thread>
) {
    companion object {
        private val JSON = Json {
            ignoreUnknownKeys = true
        }

        fun fromJson(path: Path): ThreadDump = fromModernInputStream(path.inputStream())
        fun fromString(path: Path): ThreadDump = fromLegacyInputStream(path.inputStream())

        @OptIn(ExperimentalSerializationApi::class)
        internal fun fromModernInputStream(stream: InputStream): ThreadDump {
            return JSON.decodeFromStream(serializer(), stream)
        }

        private val versionPattern = """[78]\.\d\.\d\d?.*""".toRegex()

        internal fun fromLegacyInputStream(stream: InputStream): ThreadDump {
            stream.bufferedReader().use { reader ->
                val firstLine = reader.readLine()
                val version = versionPattern.find(firstLine)?.value.orEmpty()

                reader.readLine()

                return ThreadDump(
                    version = version,
                    threads = when {
                        firstLine.contains(":") -> parseScript(reader)
                        else -> parseWebPage(reader)
                    }
                )
            }
        }

        private val scriptThreadRegex = """
            "(?<name>.*)"
            \s*CPU:\s(?<cpu>\d{1,3}\.\d{2})%
            \s*java\.lang\.Thread\.State:\s(?<state>\w+_?\w+)
            \s*(?<stack>[\S\s]+?)[\r\n]*
            """.toRegex(RegexOption.COMMENTS)

        private fun parseScript(reader: BufferedReader): List<Thread> {
            return scriptThreadRegex.findAll(reader.readText()).map { matcher ->
                val name by matcher.groups
                val cpu by matcher.groups
                val state by matcher.groups
                val stack by matcher.groups

                Thread(
                    id = name.value.hashCode(),
                    name = name.value,
                    cpuUsage = cpu.value.toDouble(),
                    state = ThreadState.valueOf(state.value),
                    isDaemon = false,
                    stacktrace = stack.value.lines().map(String::trim)
                )
            }.toList()
        }

        private fun parseWebPage(reader: BufferedReader): List<Thread> {
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

            return threads
        }
    }
}

package io.github.paulgriffith.kindling.thread.model

import io.github.paulgriffith.kindling.utils.getLogger
import io.github.paulgriffith.kindling.utils.getValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.lang.Thread.State as ThreadState

@Serializable
data class ThreadDump internal constructor(
    val version: String,
    val threads: List<Thread>,
    @SerialName("deadlocks")
    val deadlockIds: List<Int> = emptyList()
) {
    companion object {
        private val JSON = Json {
            ignoreUnknownKeys = true
        }

        private val logger = getLogger<ThreadDump>()

        fun fromStream(stream: InputStream): ThreadDump? {
            val text = stream.reader().readText()
            return try {
                JSON.decodeFromString(serializer(), text)
            } catch (ex: SerializationException) {
                logger.debug("Not a JSON string?", ex)

                val lines = text.lines()
                require(lines.size > 2) { "Not a fully formed thread dump" }
                val firstLine = lines.first()

                val deadlockIds: List<Int>
                val startingIndex: Int
                if (lines[2].contains("Deadlock")) {
                    deadlockIds = deadlocksPattern.findAll(lines[3]).map { match -> match.value.toInt() }.toList()
                    startingIndex = 5
                } else {
                    deadlockIds = emptyList()
                    startingIndex = 2
                }

                ThreadDump(
                    version = versionPattern.find(firstLine)?.value ?: firstLine,
                    threads = when {
                        firstLine.contains(":") -> parseScript(text)
                        else -> parseWebPage(lines.subList(startingIndex, lines.size))
                    },
                    deadlockIds = deadlockIds
                )
            }
        }

        private val versionPattern = """[78]\.\d\.\d\d?.*""".toRegex()

        private val deadlocksPattern = """\d+""".toRegex()

        private val scriptThreadRegex = """
            "(?<name>.*)"
            \s*CPU:\s(?<cpu>\d{1,3}\.\d{2})%
            \s*java\.lang\.Thread\.State:\s(?<state>\w+_?\w+)
            \s*(?<stack>[\S\s]+?)[\r\n]*
            """.toRegex(RegexOption.COMMENTS)

        private fun parseScript(dump: String): List<Thread> {
            return scriptThreadRegex.findAll(dump).map { matcher ->
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

        private fun parseWebPage(lines: List<String>): List<Thread> {
            val threads = mutableListOf<Thread>()
            val buffer = mutableListOf<String>()
            for (line in lines) {
                if (line.isEmpty()) {
                    continue
                }
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

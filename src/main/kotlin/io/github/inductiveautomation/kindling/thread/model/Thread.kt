package io.github.inductiveautomation.kindling.thread.model

import io.github.inductiveautomation.kindling.utils.StackTrace
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.lang.Thread.State

@Serializable
data class Thread(
    val id: Int,
    val name: String,
    val state: State,
    @SerialName("daemon")
    val isDaemon: Boolean,
    @Serializable(with = NoneAsNullStringSerializer::class)
    val system: String? = null,
    val scope: String? = null,
    val cpuUsage: Double? = null,
    val lockedMonitors: List<Monitors> = emptyList(),
    val lockedSynchronizers: List<String> = emptyList(),
    @SerialName("waitingFor")
    val blocker: Blocker? = null,
    val stacktrace: StackTrace = emptyList(),
) {
    var marked: Boolean = false

    val pool: String? = parseThreadPool(name)

    @Serializable
    data class Monitors(
        val lock: String,
        val frame: String? = null,
    )

    @Serializable
    data class Blocker(
        val lock: String,
        val owner: Int? = null,
    ) {
        override fun toString(): String = if (owner != null) {
            "$lock (owned by $owner)"
        } else {
            lock
        }
    }

    companion object {
        private val threadPoolRegex = "(?<pool>.+)-\\d+\$".toRegex()

        private val threadPoolFile = "/thread_pool_dictionary.json"

        @OptIn(ExperimentalSerializationApi::class)
        val threadPoolMap: Map<String, ThreadPoolEntry> = Json.decodeFromStream(this::class.java.getResourceAsStream(threadPoolFile)!!)

        fun parseThreadPool(threadName: String): String? {
            if ("scheduled-tag-reads" in threadName) {
                return "scheduled-tag-reads"
            }

            if ("webserver" in threadName && "acceptor" in threadName) {
                return "webserver-acceptor"
            }

            val dashTokens = threadName.split("-").map { token ->
                token.split("[").first()
                     .split("/").first()
                     .split("@").first()
                     .split("(").first()
            }

            val spaceTokens = threadName.split(" ").map { token ->
                token.split("[").first()
                    .split("/").first()
                    .split("@").first()
                    .split("(").first()
            }

            return dashTokens.asSequence().mapIndexed { index, _ ->
                dashTokens.subList(0, index + 1).joinToString("-")
            }.find {
                it in threadPoolMap.keys
            } ?: spaceTokens.asSequence().mapIndexed { index, _ ->
                spaceTokens.subList(0, index + 1).joinToString(" ")
            }.find {
                it in threadPoolMap.keys
            }
        }
    }
}

@Serializable
data class ThreadPoolEntry(
    val pattern: String,
    val name: String,
    val description: String,
    @SerialName("pool_id")
    val poolId: Int,
)
package io.github.inductiveautomation.kindling.thread.model

import io.github.inductiveautomation.kindling.core.Kindling
import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Experimental.betterThreadPools
import io.github.inductiveautomation.kindling.utils.StackTrace
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.lang.Thread.State
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

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

    var pool: String? = if (betterThreadPools.currentValue) parseThreadPool(name) else extractPool(name)
//        get() = if (betterThreadPools.currentValue) parseThreadPool(name) else extractPool(name)

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

    @OptIn(ExperimentalSerializationApi::class)
    companion object {
        private const val THREAD_POOL_MAP_ENDPOINT =
            "${Kindling.GATEWAY_ADDRESS}/system/webdev/ThreadCSVImportTool/get_thread_pool_dictionary"
        private const val LAST_UPDATED_ENDPOINT =
            "${Kindling.GATEWAY_ADDRESS}/system/webdev/ThreadCSVImportTool/get_thread_pool_dictionary_last_update"

        private const val THREAD_POOL_FILENAME = "thread_pool_dictionary.json"

        private val threadPoolFilePath = Path(System.getProperty("user.home"), ".kindling", THREAD_POOL_FILENAME)

        val threadPoolMap: Map<String, ThreadPoolEntry> by lazy {
            runBlocking {
                val client = HttpClient()

                val lastGatewayUpdate = try {
                    Instant.ofEpochMilli(client.get(LAST_UPDATED_ENDPOINT).bodyAsText().toLong())
                } catch (e: Exception) {
                    Instant.ofEpochMilli(0)
                }

                try {
                    if (Kindling.Preferences.Experimental.threadPoolsLastUpdated.currentValue < lastGatewayUpdate) { // File in disk needs update
                        val threadPoolFile = client.get(THREAD_POOL_MAP_ENDPOINT).bodyAsText()

                        // Update file on disk
                        threadPoolFilePath.outputStream().use { out ->
                            threadPoolFile.byteInputStream().use { input ->
                                input.copyTo(out)
                            }
                        }

                        Kindling.Preferences.Experimental.threadPoolsLastUpdated.currentValue = Instant.now()

                        // We already have the file in memory so just use that
                        Json.decodeFromStream(threadPoolFile.byteInputStream())
                    } else {
                        Json.decodeFromStream(threadPoolFilePath.inputStream())
                    }
                } catch (e: Exception) { // fallback to file in jar if something happens
                    Json.decodeFromStream(this::class.java.getResourceAsStream("/$THREAD_POOL_FILENAME")!!)
                }
            }
        }

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

        private val threadPoolRegex = "(?<pool>.+)-\\d+\$".toRegex()

        internal fun extractPool(name: String): String? {
            return threadPoolRegex.find(name)?.groups?.get("pool")?.value
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

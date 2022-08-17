package io.github.paulgriffith.kindling.thread.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.FileInputStream
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
        fun parseJSON(path: Path): ThreadDump {
            return JSON.decodeFromStream<ThreadDump>(path.inputStream())
        }

        fun parseLegacy(path: Path): ThreadDump {
            val file = File(path.toString())

            return FileInputStream(file).use {
                val version = file.useLines {line -> line.first() }
                if (version.contains(":")) parseScript(it) else parseWebPage(it)
            }
        }

        internal fun parseWebPage(stream: InputStream): ThreadDump {
            val version: String
            val threads = mutableListOf<Thread>()

            stream.bufferedReader().use { br ->
                version = br.readLine().split("Ignition v")[1]
                br.readLine()

                val threadString = mutableListOf<String>()
                br.lines().forEach {
                    if ((it.startsWith("Thread") || it.startsWith("Daemon")) && threadString.size > 0) {
                        threads.add(Thread.createFromLegacyWebPage(threadString))
                        threadString.clear()
                    }
                    threadString.add(it.replace("\t", ""))
                }
                threads.add(Thread.createFromLegacyWebPage(threadString))
            }
            return ThreadDump(version, threads)
        }
        internal fun parseScript(stream: InputStream): ThreadDump {
            val version: String
            val threads = mutableListOf<Thread>()
            stream.bufferedReader().use {br ->
                version = br.readLine().split("Ignition version: ")[1]
                br.readLine()

                val threadString = mutableListOf<String>()
                br.lines().forEach {
                    if (it.startsWith("\"") && threadString.size > 0) {
                        threads.add(Thread.createFromLegacyScript(threadString))
                        threadString.clear()
                    }
                    threadString.add(it.trim())
                }
                threads.add(Thread.createFromLegacyScript(threadString))
            }
            return ThreadDump(version, threads)
        }
    }
}

package io.github.paulgriffith.thread

import io.github.paulgriffith.kindling.thread.model.ThreadDump
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream

@OptIn(ExperimentalSerializationApi::class)
class ThreadViewTests : FunSpec({
    test("Thread JSON deserialization") {
        val stream = requireNotNull(ThreadViewTests::class.java.getResourceAsStream("threadDump.json"))
        ThreadDump.JSON.decodeFromStream(ThreadDump.serializer(), stream)
            .asClue { (version, threads) ->
                version shouldBe "Dev"
                threads.size shouldBe 2
            }
    }
    test("Legacy Thread Parsing") {
        val stream = requireNotNull(ThreadViewTests::class.java.getResourceAsStream("legacyWebThreadDump.txt"))
        ThreadDump.parseWebPage(stream)
            .asClue { (version, threads) ->
                version shouldBe "7.9.14 (b2020042813)"
                threads.size shouldBe 4
            }
        val stream2 = requireNotNull(ThreadViewTests::class.java.getResourceAsStream("legacyScriptThreadDump.txt"))
        ThreadDump.parseScript(stream2)
            .asClue { (version, threads) ->
                version shouldBe "8.1.1 (b2020120808)"
                threads.size shouldBe 3
            }
    }
})

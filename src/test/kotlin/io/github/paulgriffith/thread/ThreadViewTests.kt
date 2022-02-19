package io.github.paulgriffith.thread

import io.github.paulgriffith.thread.model.ThreadDump
import io.kotest.assertions.asClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.decodeFromStream

@OptIn(ExperimentalSerializationApi::class)
class ThreadViewTests : FunSpec({
    test("Thread JSON deserialization") {
        val stream = requireNotNull(ThreadViewTests::class.java.getResourceAsStream("threadDump.json"))
        ThreadView.JSON.decodeFromStream<ThreadDump>(stream)
            .asClue { (version, threads) ->
                version shouldBe "Dev"
                threads.size shouldBe 2
            }
    }
})

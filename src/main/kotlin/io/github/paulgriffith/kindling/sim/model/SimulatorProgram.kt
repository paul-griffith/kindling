package io.github.paulgriffith.kindling.sim.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.csv.Csv
import kotlinx.serialization.csv.config.QuoteMode.ALL


typealias SimulatorProgram = MutableList<ProgramItem>
@OptIn(ExperimentalSerializationApi::class)
fun SimulatorProgram.toSimulatorCsv(): String {
    val csv = Csv {
        quoteMode = ALL
        hasHeaderRecord = true
    }
    return csv.encodeToString(ListSerializer(ProgramItem.serializer()), this)
}

@Serializable
data class ProgramItem(
    @SerialName("Time Interval")
    var timeInterval: Int,
    @SerialName("Browse Path")
    var browsePath: String,
    @SerialName("Value Source")
    var valueSource: String,
    @SerialName("Data Type")
    var dataType: String,
)
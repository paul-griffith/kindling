package io.github.paulgriffith.kindling.sim.model

import io.github.paulgriffith.kindling.sim.model.QualityCodes.Good
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.csv.Csv
import kotlinx.serialization.csv.config.QuoteMode.ALL
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

typealias SimulatorProgram = List<ProgramItem>
@OptIn(ExperimentalSerializationApi::class)
fun SimulatorProgram.toSimulatorCsv(): String {
    return Csv {
        quoteMode = ALL
        hasHeaderRecord = true
    }.encodeToString(ListSerializer(ProgramItem.serializer()), this)
}

@Serializable
data class ProgramItem(
    @SerialName("Time Interval")
    var timeInterval: Int = 1000,
    @SerialName("Browse Path")
    var browsePath: String,
    @SerialName("Value Source")
    var valueSource: SimulatorFunction? = null,
    @SerialName("Data Type")
    var dataType: ProgramDataType? = null,
)

@Serializable(with=ProgramDataTypeSerializer::class)
enum class ProgramDataType(val exportName: String) {
    BOOLEAN("boolean"),
    INT16("int16"),
    UINT16("uint16"),
    INT32("int32"),
    UINT32("uint32"),
    INT64("int64"),
    FLOAT("float"),
    DOUBLE("double"),
    STRING("string"),
    DATETIME("datetime");
}

@Suppress("unused")
@Serializable(with=SimulatorFunctionSerializer::class)
sealed interface SimulatorFunction {
    val name: String
    val parameters: kotlin.collections.List<SimulatorFunctionParameter<*>>

    @Serializable
    class Cosine(
        override val name: String = "cosine",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        )
    ) : SimulatorFunction

    @Serializable
    class Square(
        override val name: String = "square",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        )
    ) : SimulatorFunction

    @Serializable
    class Triangle(
        override val name: String = "triangle",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        )
    ) : SimulatorFunction

    @Serializable
    class Ramp(
        override val name: String = "ramp",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        )
    ) : SimulatorFunction

    @Serializable
    class Realistic(
        override val name: String = "realistic",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.SetPoint(),
            SimulatorFunctionParameter.Proportion(),
            SimulatorFunctionParameter.Integral(),
            SimulatorFunctionParameter.Derivative(),
            SimulatorFunctionParameter.Repeat(),
        )
    ) : SimulatorFunction

    @Serializable
    class Random(
        override val name: String = "random",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Repeat(),
        )
    ) : SimulatorFunction

    @Serializable
    class List(
        override val name: String = "list",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.List(),
            SimulatorFunctionParameter.Repeat(),
        )
    ) : SimulatorFunction

    @Serializable
    class QV(
        override val name: String = "qv",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Value(),
            SimulatorFunctionParameter.QualityCode(),
        )
    ) : SimulatorFunction

    @Serializable
    class ReadOnly(
        override val name: String = "readonly",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Value(),
        )
    ) : SimulatorFunction

    @Serializable
    class Writable(
        override val name: String = "writeable",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<*>> = listOf(
            SimulatorFunctionParameter.Value()
        )
    ) : SimulatorFunction


    companion object {
        val defaultFunction: SimulatorFunction
            get() = Cosine()
    }
}

//@Serializable
sealed class SimulatorFunctionParameter<T> private constructor(
    val name: String,
    private val defaultValue: T,
    var value: T,
) {
    constructor(name: String, defaultValue: T) : this(name, defaultValue, defaultValue)

    class Min : SimulatorFunctionParameter<Int>("min", 0)
    class Max : SimulatorFunctionParameter<Int>("max", 100)
    class Period : SimulatorFunctionParameter<Int>("period", 10)
    class SetPoint : SimulatorFunctionParameter<Int>("setPoint", 0)
    class Proportion : SimulatorFunctionParameter<Float>("proportion", 1.2F)
    class Integral : SimulatorFunctionParameter<Float>("integral", 0.06F)
    class Derivative : SimulatorFunctionParameter<Float>("derivative", 0.25F)
    class QualityCode : SimulatorFunctionParameter<QualityCodes>("qualityCode", Good)
    class Repeat : SimulatorFunctionParameter<Boolean>("repeat", true)
    class List : SimulatorFunctionParameter<MutableList<String>>("list", mutableListOf())
    class Value: SimulatorFunctionParameter<String>("value", "Any Value")

    operator fun component1(): String = name
    operator fun component2(): T = value
}

@Serializable
enum class QualityCodes {
    Good,
    Bad,
    Uncertain,
}

object SimulatorFunctionSerializer : KSerializer<SimulatorFunction> {
    override val descriptor = String.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SimulatorFunction) {
        val output = buildString {
            append(value.name)
            append(
                value.parameters.joinToString(",", "(", ")") {
                    when (val paramValue = it.value) {
                        is List<*> -> {
                            paramValue.joinToString(",")
                        }
                        else -> paramValue.toString()
                    }
                }
            )
        }
        encoder.encodeString(output)
    }

    override fun deserialize(decoder: Decoder): SimulatorFunction {
        throw NotImplementedError("Importing of Device Simulator CSV Files is not Supported.")
    }
}

class ProgramDataTypeSerializer : KSerializer<ProgramDataType> {
    val delegate = String.serializer()
    override val descriptor = PrimitiveSerialDescriptor("ProgramDataType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ProgramDataType) {
        encoder.encodeString(value.exportName)
    }
    override fun deserialize(decoder: Decoder): ProgramDataType {
        val stringRepresentation = decoder.decodeSerializableValue(delegate)
        return ProgramDataType.valueOf(stringRepresentation.uppercase())
    }
}

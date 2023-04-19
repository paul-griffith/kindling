package io.github.paulgriffith.kindling.sim.model

import io.github.paulgriffith.kindling.sim.model.QualityCodes.Good
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Polymorphic
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.csv.Csv
import kotlinx.serialization.csv.config.QuoteMode.ALL
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.apache.poi.ss.formula.functions.T
import sun.jvm.hotspot.oops.CellTypeState.value

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

// ramp(0, 100, 10, true)
// random(0, 100, true)

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

// function.name(param1.value, param2.value)


@Serializable
sealed interface SimulatorFunction {
    val name: String
    val parameters: kotlin.collections.List<SimulatorFunctionParameter<*>>

    @Serializable
    class Sine private constructor(
        override val name: String = "sine",
        @Polymorphic
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<*>> = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        )
    ) : SimulatorFunction

    @Serializable
    class Cosine : SimulatorFunction {
        override val name = "cosine"
        override val parameters = listOf<SimulatorFunctionParameter<*>>(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        )
    }

    @Serializable
    class Square : SimulatorFunction {
        override val name = "square"
        override val parameters = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        )
    }

    @Serializable
    class Triangle : SimulatorFunction {
        override val name = "triangle"
        override val parameters = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        )
    }

    @Serializable
    class Ramp : SimulatorFunction {
        override val name = "ramp"
        override val parameters = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        )
    }

    @Serializable
    class Realistic : SimulatorFunction {
        override val name = "realistic"
        override val parameters = listOf(
            SimulatorFunctionParameter.SetPoint(),
            SimulatorFunctionParameter.Proportion(),
            SimulatorFunctionParameter.Integral(),
            SimulatorFunctionParameter.Derivative(),
            SimulatorFunctionParameter.Repeat(),
        )
    }

    @Serializable
    class Random : SimulatorFunction {
        override val name = "random"
        override val parameters = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Repeat(),
        )
    }

    @Serializable
    class List : SimulatorFunction {
        override val name = "list"
        override val parameters = listOf(
            SimulatorFunctionParameter.List(),
            SimulatorFunctionParameter.Repeat(),
        )
    }

    @Serializable
    class QV : SimulatorFunction {
        override val name = "qv"
        override val parameters = listOf(
            SimulatorFunctionParameter.Value(),
            SimulatorFunctionParameter.QualityCode(),
        )
    }

    @Serializable
    class ReadOnly : SimulatorFunction {
        override val name = "readonly"
        override val parameters = listOf(
            SimulatorFunctionParameter.Value(),
        )
    }

    @Serializable
    class Writable : SimulatorFunction {
        override val name = "writeable"
        override val parameters = listOf(
            SimulatorFunctionParameter.Value()
        )
    }


    companion object {
        val defaultFunction: SimulatorFunction
            get() = Cosine()
    }
}

@Serializable
sealed class SimulatorFunctionParameter<T> private constructor(
    private val name: String,
    private val defaultValue: T,
    var value: T,
) {
    constructor(name: String, defaultValue: T) : this(name, defaultValue, defaultValue)

    @Serializable
    class Min : SimulatorFunctionParameter<Int>("min", 0)
    @Serializable
    class Max : SimulatorFunctionParameter<Int>("max", 100)
    @Serializable
    class Period : SimulatorFunctionParameter<Int>("period", 10)
    @Serializable
    class SetPoint : SimulatorFunctionParameter<Int>("setPoint", 0)
    @Serializable
    class Proportion : SimulatorFunctionParameter<Float>("proportion", 1.2F)
    @Serializable
    class Integral : SimulatorFunctionParameter<Float>("integral", 0.06F)
    @Serializable
    class Derivative : SimulatorFunctionParameter<Float>("derivative", 0.25F)
    @Serializable
    class QualityCode : SimulatorFunctionParameter<QualityCodes>("qualityCode", Good)
    @Serializable
    class Repeat : SimulatorFunctionParameter<Boolean>("repeat", true)
    @Serializable
    class List : SimulatorFunctionParameter<MutableList<String>>("list", mutableListOf())
    @Serializable
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

class SimulatorFunctionSerializer : KSerializer<SimulatorFunction> {
    private val delegate =  String.serializer()
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("SimulatorFunction", PrimitiveKind.STRING)

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

class FunctionParameterSerializer(private val dataSerializer: KSerializer<T>) : KSerializer<SimulatorFunctionParameter<T>> {
    override val descriptor = dataSerializer.descriptor
    override fun serialize(encoder: Encoder, value: SimulatorFunctionParameter<T>) {
        dataSerializer.serialize(encoder, value.value)
    }

    override fun deserialize(decoder: Decoder): SimulatorFunctionParameter<T> {
        throw NotImplementedError("Importing of Device Simulator CSV Files is not Supported.")
    }
}

private val module = SerializersModule {
    polymorphicDefaultSerializer(SimulatorFunctionParameter::class) { instance ->
        when (instance) {
            is SimulatorFunctionParameter.Min
            is SimulatorFunctionParameter.Max
            is SimulatorFunctionParameter.Period
            is SimulatorFunctionParameter.SetPoint -> {

            }
            is SimulatorFunctionParameter.Proportion
            is SimulatorFunctionParameter.Integral
            is SimulatorFunctionParameter.Derivative -> {

            }
            is SimulatorFunctionParameter.QualityCode -> {

            }
            is SimulatorFunctionParameter.Repeat -> {

            }
            is SimulatorFunctionParameter.List -> {

            }
            is SimulatorFunctionParameter.Value -> {

            }
        }
    }
}


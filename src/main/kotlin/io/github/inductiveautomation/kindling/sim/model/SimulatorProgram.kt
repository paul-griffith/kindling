package io.github.inductiveautomation.kindling.sim.model

import io.github.inductiveautomation.kindling.sim.model.QualityCodes.Good
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
import java.nio.file.Path
import java.text.DecimalFormat
import java.time.Duration
import java.time.Instant
import kotlin.io.path.outputStream
import kotlin.random.nextUInt
import kotlin.random.nextULong
import kotlin.reflect.KClass

typealias SimulatorProgram = MutableList<ProgramItem>

@OptIn(ExperimentalSerializationApi::class)
fun List<ProgramItem>.toSimulatorCsv(): String {
    return Csv {
        quoteMode = ALL
        hasHeaderRecord = true
    }.encodeToString(ListSerializer(ProgramItem.serializer()), this)
}

fun List<ProgramItem>.exportToFile(filePath: Path) {
    if (isEmpty()) return
    filePath.outputStream().use { out ->
        this.toSimulatorCsv().byteInputStream().use { input ->
            input.copyTo(out)
        }
    }
}

@Serializable
data class ProgramItem(
    @SerialName("Time Interval")
    var timeInterval: Int,
    @SerialName("Browse Path")
    var browsePath: String,
    @SerialName("Value Source")
    var valueSource: SimulatorFunction? = null,
    @SerialName("Data Type")
    var dataType: ProgramDataType,
    @Transient
    var deviceName: String = "",
) {
    constructor(
        timeInterval: Int = 1000,
        ignitionBrowsePath: String,
        valueSource: SimulatorFunction?,
        dataType: ProgramDataType,
    ) : this(
        timeInterval = timeInterval,
        browsePath = ignitionBrowsePath.substringAfter("]"),
        valueSource = valueSource,
        dataType = dataType,
        deviceName = ignitionBrowsePath.substringBefore("]").substringAfter("["),
    )
}

@Serializable(with = ProgramDataTypeSerializer::class)
enum class ProgramDataType(val exportName: String) {
    BOOLEAN("boolean"),
    INT16("int16"),
    UINT16("uint16"),
    INT32("int32"),
    UINT32("uint32"),
    INT64("int64"),
    UINT64("uint64"),
    FLOAT("float"),
    DOUBLE("double"),
    STRING("string"),
    DATETIME("datetime"),
    ;

    companion object {
        val NUMERIC_TYPES = listOf(
            INT16,
            UINT16,
            INT32,
            UINT32,
            INT64,
            UINT64,
            FLOAT,
            DOUBLE,
        )
        val ALL_TYPES = values().toList()
    }
}

@Suppress("unused")
@Serializable(with = SimulatorFunctionSerializer::class)
sealed interface SimulatorFunction {
    val name: String
    val parameters: kotlin.collections.List<SimulatorFunctionParameter<*>>

    @Serializable
    class Sine(
        override val name: String = "sine",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        ),
    ) : SimulatorFunction

    @Serializable
    class Cosine(
        override val name: String = "cosine",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Period(),
            SimulatorFunctionParameter.Repeat(),
        ),
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
        ),
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
        ),
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
        ),
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
        ),
    ) : SimulatorFunction

    @Serializable
    class Random(
        override val name: String = "random",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Min(),
            SimulatorFunctionParameter.Max(),
            SimulatorFunctionParameter.Repeat(),
        ),
    ) : SimulatorFunction

    @Serializable
    class List(
        override val name: String = "list",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.List(),
            SimulatorFunctionParameter.Repeat(),
        ),
    ) : SimulatorFunction

    @Serializable
    class QV(
        override val name: String = "qv",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Value(),
            SimulatorFunctionParameter.QualityCode(),
        ),
    ) : SimulatorFunction

    @Serializable
    class ReadOnly(
        override val name: String = "readonly",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<out Any>> = listOf(
            SimulatorFunctionParameter.Value(),
        ),
    ) : SimulatorFunction

    @Serializable
    class Writable(
        override val name: String = "writeable",
        @Transient
        override val parameters: kotlin.collections.List<SimulatorFunctionParameter<*>> = listOf(
            SimulatorFunctionParameter.Value(),
        ),
    ) : SimulatorFunction

    companion object {
        fun defaultFunctionForType(type: ProgramDataType): KClass<*> {
            return if (type in ProgramDataType.NUMERIC_TYPES) Sine::class else Writable::class
        }

        val functions = mapOf<KClass<*>, () -> SimulatorFunction>(
            Sine::class to { Sine() },
            Cosine::class to { Cosine() },
            Square::class to { Square() },
            Ramp::class to { Ramp() },
            List::class to { List() },
            QV::class to { QV() },
            Random::class to { Random() },
            ReadOnly::class to { ReadOnly() },
            Realistic::class to { Realistic() },
            Triangle::class to { Triangle() },
            Writable::class to { Writable() },
        )

        val compatibleTypes = mapOf(
            Sine::class to ProgramDataType.NUMERIC_TYPES,
            Cosine::class to ProgramDataType.NUMERIC_TYPES,
            Square::class to ProgramDataType.NUMERIC_TYPES,
            Ramp::class to ProgramDataType.NUMERIC_TYPES,
            List::class to ProgramDataType.ALL_TYPES,
            QV::class to ProgramDataType.ALL_TYPES,
            Random::class to ProgramDataType.NUMERIC_TYPES + ProgramDataType.BOOLEAN,
            ReadOnly::class to ProgramDataType.ALL_TYPES,
            Realistic::class to ProgramDataType.NUMERIC_TYPES,
            Triangle::class to ProgramDataType.NUMERIC_TYPES,
            Writable::class to ProgramDataType.ALL_TYPES,
        )

        fun randomValueForDataType(type: ProgramDataType): String {
            val df = DecimalFormat("#.##")
            return when (type) {
                ProgramDataType.BOOLEAN -> listOf(true, false).random()
                ProgramDataType.INT16 -> kotlin.random.Random.nextBits(16)
                ProgramDataType.UINT16 -> kotlin.random.Random.nextBits(16).toShort()
                ProgramDataType.INT32 -> kotlin.random.Random.nextInt()
                ProgramDataType.UINT32 -> kotlin.random.Random.nextUInt()
                ProgramDataType.INT64 -> kotlin.random.Random.nextLong()
                ProgramDataType.UINT64 -> kotlin.random.Random.nextULong()
                ProgramDataType.FLOAT -> df.format(kotlin.random.Random.nextFloat() * 100.0)
                ProgramDataType.DOUBLE -> df.format(kotlin.random.Random.nextDouble() * 100.0)
                ProgramDataType.STRING -> {
                    val chars = ('a'..'b') + ('A'..'B') + ('0'..'9')
                    val numberOfLetters = (5..15).random()
                    buildString {
                        repeat(numberOfLetters) { append(chars.random()) }
                    }
                }
                ProgramDataType.DATETIME -> {
                    val endTime = Instant.now()
                    val startTime = endTime - Duration.ofDays(365)
                    kotlin.random.Random.nextLong(startTime.toEpochMilli(), endTime.toEpochMilli())
                }
            }.toString()
        }
    }
}

sealed class SimulatorFunctionParameter<T>(
    val name: String,
    val defaultValue: T,
    var value: T,
) {

    class Min(value: Int = 0) : SimulatorFunctionParameter<Int>("min", 0, value)
    class Max(value: Int = 100) : SimulatorFunctionParameter<Int>("max", 100, value)
    class Period(value: Int = 10) : SimulatorFunctionParameter<Int>("period", 10, value)
    class SetPoint(value: Int = 0) : SimulatorFunctionParameter<Int>("setPoint", 0, value)
    class Proportion(value: Double = 1.2) : SimulatorFunctionParameter<Double>("proportion", 1.2, value)
    class Integral(value: Double = 0.06) : SimulatorFunctionParameter<Double>("integral", 0.06, value)
    class Derivative(value: Double = 0.25) : SimulatorFunctionParameter<Double>("derivative", 0.25, value)
    class QualityCode(value: QualityCodes = Good) : SimulatorFunctionParameter<QualityCodes>("qualityCode", Good, value)
    class Repeat(value: Boolean = true) : SimulatorFunctionParameter<Boolean>("repeat", true, value)
    class List(
        value: MutableList<String> = mutableListOf(),
    ) : SimulatorFunctionParameter<MutableList<String>>("list", mutableListOf(), value)
    class Value(value: String = "Any Value") : SimulatorFunctionParameter<String>("value", "Any Value", value)

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
            if (value !is SimulatorFunction.Writable) {
                append(value.name)
                append(
                    value.parameters.joinToString(",", "(", ")") {
                        when (val paramValue = it.value) {
                            is List<*> -> {
                                paramValue.joinToString(",")
                            }
                            else -> paramValue.toString()
                        }
                    },
                )
            } else {
                append(value.parameters.first().value.toString())
            }
        }
        encoder.encodeString(output)
    }

    override fun deserialize(decoder: Decoder): SimulatorFunction {
        throw NotImplementedError("Importing of Device Simulator CSV Files is not Supported.")
    }
}

object ProgramDataTypeSerializer : KSerializer<ProgramDataType> {
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

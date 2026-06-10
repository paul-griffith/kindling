package io.github.inductiveautomation.kindling.serial.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.ObjectStreamConstants.SC_BLOCK_DATA
import java.io.ObjectStreamConstants.SC_ENUM
import java.io.ObjectStreamConstants.SC_EXTERNALIZABLE
import java.io.ObjectStreamConstants.SC_SERIALIZABLE
import java.io.ObjectStreamConstants.SC_WRITE_METHOD

/**
 * A structural model of a Java serialization stream, as described by the
 * [Java Object Serialization Specification](https://docs.oracle.com/en/java/javase/17/docs/specs/serialization/protocol.html),
 * parsed without requiring any of the serialized classes to be present on the classpath.
 *
 * The stream's back-references (`TC_REFERENCE`) are kept symbolic as [ReferenceNode]s carrying the wire handle,
 * so the model is always a strict tree even when the serialized object graph contains cycles.
 * Every node that is assigned a wire handle records it, so an index of handle to node can be rebuilt by
 * walking the tree.
 */
@Serializable
data class SerializedStream(
    val contents: List<StreamContent>,
)

/** A single element of a serialization stream; the `content` production of the stream grammar. */
@Serializable
sealed interface StreamContent

/** A value in an object field or array element position. */
@Serializable
sealed interface FieldValue

/**
 * Anything that can appear where the grammar expects a `classDesc`:
 * an inline [ClassDesc] or a [ReferenceNode] pointing back to an earlier one.
 * `null` class descriptors are modeled as Kotlin nulls at the use site.
 */
@Serializable
sealed interface ClassDescRef

@Serializable
sealed interface PrimitiveValue : FieldValue

@Serializable
@SerialName("byte")
data class ByteValue(val value: Byte) : PrimitiveValue

@Serializable
@SerialName("char")
data class CharValue(val value: Char) : PrimitiveValue

@Serializable
@SerialName("double")
data class DoubleValue(val value: Double) : PrimitiveValue

@Serializable
@SerialName("float")
data class FloatValue(val value: Float) : PrimitiveValue

@Serializable
@SerialName("int")
data class IntValue(val value: Int) : PrimitiveValue

@Serializable
@SerialName("long")
data class LongValue(val value: Long) : PrimitiveValue

@Serializable
@SerialName("short")
data class ShortValue(val value: Short) : PrimitiveValue

@Serializable
@SerialName("boolean")
data class BooleanValue(val value: Boolean) : PrimitiveValue

/** A class descriptor; either a [RegularClassDesc] (`TC_CLASSDESC`) or a [ProxyClassDesc] (`TC_PROXYCLASSDESC`). */
@Serializable
sealed interface ClassDesc :
    StreamContent,
    ClassDescRef {
    val handle: Int
    val annotation: List<StreamContent>
    val superClass: ClassDescRef?
}

@Serializable
@SerialName("classDesc")
data class RegularClassDesc(
    val name: String,
    val serialVersionUID: Long,
    override val handle: Int,
    val flags: Byte,
    val fields: List<FieldDesc> = emptyList(),
    override val annotation: List<StreamContent> = emptyList(),
    override val superClass: ClassDescRef? = null,
) : ClassDesc {
    val isSerializable: Boolean get() = flags hasFlag SC_SERIALIZABLE
    val isExternalizable: Boolean get() = flags hasFlag SC_EXTERNALIZABLE
    val hasWriteMethod: Boolean get() = flags hasFlag SC_WRITE_METHOD
    val hasBlockData: Boolean get() = flags hasFlag SC_BLOCK_DATA
    val isEnum: Boolean get() = flags hasFlag SC_ENUM
}

@Serializable
@SerialName("proxyClassDesc")
data class ProxyClassDesc(
    override val handle: Int,
    val interfaces: List<String> = emptyList(),
    override val annotation: List<StreamContent> = emptyList(),
    override val superClass: ClassDescRef? = null,
) : ClassDesc

/**
 * The declared shape of a single field within a [RegularClassDesc].
 * [typeName] is the declared JVM type descriptor (e.g. `Ljava/lang/String;`), present only for
 * object and array types.
 */
@Serializable
data class FieldDesc(
    val typeCode: Char,
    val name: String,
    val typeName: String? = null,
)

@Serializable
@SerialName("object")
data class ObjectNode(
    val classDesc: ClassDescRef?,
    val handle: Int,
    val classData: List<ClassData> = emptyList(),
) : StreamContent,
    FieldValue

/**
 * The serialized data contributed by one class in an object's inheritance chain.
 * [annotation] is non-null if the class wrote custom data (`writeObject`/`writeExternal`);
 * its contents are structurally parsed but semantically opaque without the class.
 */
@Serializable
data class ClassData(
    val className: String,
    val fields: List<FieldData> = emptyList(),
    val annotation: List<StreamContent>? = null,
)

@Serializable
data class FieldData(
    val name: String,
    val value: FieldValue,
)

@Serializable
@SerialName("class")
data class ClassNode(
    val classDesc: ClassDescRef?,
    val handle: Int,
) : StreamContent,
    FieldValue

@Serializable
@SerialName("array")
data class ArrayNode(
    val classDesc: ClassDescRef?,
    val className: String,
    val handle: Int,
    val elements: List<FieldValue> = emptyList(),
) : StreamContent,
    FieldValue

@Serializable
@SerialName("string")
data class StringNode(
    val handle: Int,
    val value: String,
) : StreamContent,
    FieldValue

@Serializable
@SerialName("enum")
data class EnumNode(
    val classDesc: ClassDescRef?,
    val handle: Int,
    val constantName: String,
) : StreamContent,
    FieldValue

/** A symbolic back-reference (`TC_REFERENCE`) to the node that was assigned [handle] earlier in the stream. */
@Serializable
@SerialName("reference")
data class ReferenceNode(val handle: Int) :
    StreamContent,
    FieldValue,
    ClassDescRef

@Serializable
@SerialName("null")
data object NullNode : StreamContent, FieldValue

@Serializable
@SerialName("blockData")
data class BlockDataNode(val data: ByteArray) : StreamContent {
    override fun equals(other: Any?): Boolean = this === other || (other is BlockDataNode && data.contentEquals(other.data))

    override fun hashCode(): Int = data.contentHashCode()
}

/** A `TC_EXCEPTION` marker: serialization was aborted and [throwable] was written in place of the remaining data. */
@Serializable
@SerialName("exception")
data class ExceptionNode(val throwable: StreamContent) : StreamContent

/** A `TC_RESET` marker: all handles assigned before this point are discarded. */
@Serializable
@SerialName("reset")
data object ResetNode : StreamContent

/**
 * A region of the stream that could not be parsed, starting at [offset].
 * Produced instead of throwing so that everything successfully parsed before the failure is preserved;
 * common causes are truncated streams, `Externalizable` data written with protocol version 1
 * (which is unparseable without the class), and writes aborted mid-stream with `TC_EXCEPTION`.
 */
@Serializable
@SerialName("unparseable")
data class UnparseableNode(
    val offset: Int,
    val message: String?,
    val bytes: ByteArray,
) : StreamContent {
    override fun equals(other: Any?): Boolean = this === other || (other is UnparseableNode && offset == other.offset && message == other.message && bytes.contentEquals(other.bytes))

    override fun hashCode(): Int = (offset * 31 + message.hashCode()) * 31 + bytes.contentHashCode()
}

internal infix fun Byte.hasFlag(flag: Byte): Boolean = toInt() and flag.toInt() != 0

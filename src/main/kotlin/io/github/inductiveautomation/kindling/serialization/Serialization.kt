package io.github.inductiveautomation.kindling.serialization

import deser.SerializationDumper
import io.github.inductiveautomation.kindling.serialization.Serialized.ClassDesc
import io.github.inductiveautomation.kindling.serialization.Serialized.Field.ArrayField
import io.github.inductiveautomation.kindling.serialization.Serialized.Field.ObjectField
import io.github.inductiveautomation.kindling.serialization.Serialized.Field.PrimitiveField
import io.github.inductiveautomation.kindling.utils.ByteArraySerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.io.ObjectStreamConstants
import java.io.ObjectStreamConstants.SC_BLOCK_DATA
import java.io.ObjectStreamConstants.SC_EXTERNALIZABLE
import java.io.ObjectStreamConstants.SC_SERIALIZABLE
import java.io.ObjectStreamConstants.SC_WRITE_METHOD
import java.io.ObjectStreamConstants.STREAM_MAGIC
import java.io.ObjectStreamConstants.STREAM_VERSION
import java.io.ObjectStreamConstants.TC_ARRAY
import java.io.ObjectStreamConstants.TC_BLOCKDATA
import java.io.ObjectStreamConstants.TC_BLOCKDATALONG
import java.io.ObjectStreamConstants.TC_CLASS
import java.io.ObjectStreamConstants.TC_CLASSDESC
import java.io.ObjectStreamConstants.TC_ENDBLOCKDATA
import java.io.ObjectStreamConstants.TC_ENUM
import java.io.ObjectStreamConstants.TC_LONGSTRING
import java.io.ObjectStreamConstants.TC_NULL
import java.io.ObjectStreamConstants.TC_OBJECT
import java.io.ObjectStreamConstants.TC_PROXYCLASSDESC
import java.io.ObjectStreamConstants.TC_REFERENCE
import java.io.ObjectStreamConstants.TC_STRING
import java.io.UTFDataFormatException
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.experimental.and
import kotlin.io.path.Path
import kotlin.io.path.readBytes

@OptIn(ExperimentalStdlibApi::class)
private class JavaSerializationReader(bytes: ByteArray) : Sequence<Serialized?> {
    private val handleCursor = AtomicInteger(ObjectStreamConstants.baseWireHandle)
    private val cache: MutableMap<Int, Serialized> = mutableMapOf()

    private val data: ByteBuffer = ByteBuffer.wrap(bytes)

    init {
        require(data.getShort() == STREAM_MAGIC) { "Invalid STREAM_MAGIC" }
        require(data.getShort() == STREAM_VERSION) { "Invalid STREAM_VERSION" }

        println(bytes.toHexString())
    }

    override operator fun iterator(): Iterator<Serialized?> {
        return sequence {
            while (data.hasRemaining()) {
                yield(readContentElement())
            }
        }.iterator()
    }

    private fun readContentElement(): Serialized? {
        return when (data.peek()) {
            TC_OBJECT -> readNewObject()
            TC_CLASS -> readNewClass()
            TC_ARRAY -> readNewArray()
            TC_STRING, TC_LONGSTRING -> readNewString()
            TC_ENUM -> readNewEnum()
            TC_CLASSDESC, TC_PROXYCLASSDESC -> readClassDesc()
            TC_REFERENCE -> readReference()
            TC_NULL -> readNull()
            TC_BLOCKDATA -> readBlockData()
            TC_BLOCKDATALONG -> readLongBlockData()
            else -> unexpectedByte()
        }
    }

    private fun readNewEnum(): Serialized.Enum {
        checkNextByte(TC_ENUM, "TC_ENUM")

        val ref = handleCursor.getAndIncrement()

        return Serialized.Enum(
            readClassDesc()!!,
            readNewString().data,
        ).also {
            cache[ref] = it
        }
    }

    private fun readNewObject(): Serialized {
        checkNextByte(TC_OBJECT, "TC_OBJECT")
        val classDesc = checkNotNull(readClassDesc()) { "Class cannot be null" }

        val ref = handleCursor.getAndIncrement()

        val classes = sequence<ClassDesc> {
            yield(classDesc)
            var soup = classDesc.superClass
            while (soup != null) {
                yield(soup)
                soup = soup.superClass
            }
        }.toList().reversed()

        val map = classes.associate { desc ->
            when (desc) {
                is ClassDesc.Local -> {
                    val values = desc.fields.mapTo(mutableListOf()) { field ->
                        readFieldValue(field.typeCode)
                    }

                    // Read object annotations if the right flags are set
                    if (desc.serializable && desc.writeMethod || desc.externalizable && desc.blockData) {
                        while (data.peek() != TC_ENDBLOCKDATA) {
                            values.add(readContentElement())
                        }

                        data.get()
                    }
                    desc.name to values
                }

                is ClassDesc.Proxy -> {
                    desc.proxyInterfaceNames.first() to emptyList()
                }
            }
        }

        return Serialized.NewObject(
            classDesc = classes,
            classData = map
        ).also {
            cache[ref] = it
        }
    }

    private fun readClassDesc(): ClassDesc? {
        return when (data.peek()) {
            TC_CLASSDESC -> readLocalClassDesc()
            TC_PROXYCLASSDESC -> readProxyClassDesc()
            TC_REFERENCE -> readReference() as? ClassDesc
            TC_NULL -> readNull()

            else -> unexpectedByte()
        }
    }

    private fun readLocalClassDesc(): ClassDesc.Local {
        checkNextByte(TC_CLASSDESC, "TC_CLASSDESC")
        val className = data.readUtf()

        val serialVersionUid = data.getLong()

        val ref = handleCursor.getAndIncrement()

        val flags = data.get()

        val fields = List(data.getShort().toInt()) {
            readFieldDesc()
        }

        val annotation = readClassAnnotation().toList()

        return ClassDesc.Local(
            name = className,
            serialVersionUid = serialVersionUid,
            writeMethod = flags and SC_WRITE_METHOD == SC_WRITE_METHOD,
            serializable = flags and SC_SERIALIZABLE == SC_SERIALIZABLE,
            externalizable = flags and SC_EXTERNALIZABLE == SC_EXTERNALIZABLE,
            blockData = flags and SC_BLOCK_DATA == SC_BLOCK_DATA,
            fields = fields,
            annotation = (readClassDesc() as? ClassDesc.Local)?.let { annotationDesc ->
                Serialized.NewObject(listOf(annotationDesc), mapOf(annotationDesc.name to annotation))
            },
            superClass = null
        ).also {
            cache[ref] = it
        }
    }

    private fun readProxyClassDesc(): ClassDesc.Proxy {
        checkNextByte(TC_PROXYCLASSDESC, "TC_PROXYCLASSDESC")

        val ref = handleCursor.getAndIncrement()

        val count = data.getInt()
        val proxyInterfaceNames = List(count) {
            data.readUtf()
        }

        readClassAnnotation()

        return ClassDesc.Proxy(
            proxyInterfaceNames,
            readClassDesc()
        ).also {
            cache[ref] = it
        }
    }

    private fun readClassAnnotation() = sequence {
        while (data.peek() != TC_ENDBLOCKDATA) {
            yield(readContentElement())
        }

        // move past the END_BLOCK_DATA
        data.get()
    }

    private fun readFieldDesc(): Serialized.Field {
        val typeCode = data.get()
        val fieldName = data.readUtf()
        return when (val code = Char(typeCode.toUShort())) {
            in primitiveCodeMap -> PrimitiveField(fieldName, code)
            '[' -> {
                val componentType = readNewString().data
                ArrayField(fieldName, componentType)
            }

            'L' -> {
                val objectType = readNewString().data
                ObjectField(fieldName, objectType)
            }

            else -> unexpectedByte()
        }
    }

    private fun readNewString(): Serialized.UtfString {
        return when (data.peek()) {
            TC_STRING -> readString()
            TC_LONGSTRING -> readLongString()
            TC_REFERENCE -> readReference() as Serialized.UtfString
            else -> unexpectedByte()
        }
    }

    private fun <T> readNull(): T? {
        checkNextByte(TC_NULL, "TC_NULL")
        return null
    }

    private fun readReference(): Serialized? {
        checkNextByte(TC_REFERENCE, "TC_REFERENCE")
        val handle = data.getInt()
        return cache[handle]
    }

    private fun readString(): Serialized.UtfString {
        checkNextByte(TC_STRING, "TC_STRING")

        val ref = handleCursor.getAndIncrement()

        return Serialized.UtfString(
            data = data.readUtf()
        ).also {
            cache[ref] = it
        }
    }

    private fun readLongString(): Serialized.UtfString {
        checkNextByte(TC_LONGSTRING, "TC_LONGSTRING")

        val ref = handleCursor.getAndIncrement()

        return Serialized.UtfString(
            data = data.readUtf(data.getLong().toInt())
        ).also {
            cache[ref] = it
        }
    }

    private fun readNewArray(): Serialized.Array {
        checkNextByte(TC_ARRAY, "TC_ARRAY")
        val classDesc = readClassDesc()
        require(classDesc is ClassDesc.Local)

        val typeCode = classDesc.name.first()

        val ref = handleCursor.getAndIncrement()

        val size = data.getInt()

        return Serialized.Array(
            List(size) {
                readFieldValue(typeCode)
            }
        ).also {
            cache[ref] = it
        }
    }

    private fun readNewClass(): ClassDesc {
        checkNextByte(TC_CLASS, "TC_CLASS")
        return requireNotNull(readClassDesc()).also {
            cache[handleCursor.getAndIncrement()] = it
        }
    }

    private fun readFieldValue(typeCode: Char): Serialized? {
        return when (typeCode) {
            in primitiveCodeMap -> Serialized.Primitive(primitiveCodeMap.getValue(typeCode).invoke(data))
            '[' -> when (data.peek()) {
                TC_NULL -> null
                TC_ARRAY -> readNewArray()
                TC_REFERENCE -> readReference()
                else -> unexpectedByte()
            }

            'L' -> when (data.peek()) {
                TC_OBJECT -> readNewObject()
                TC_REFERENCE -> readReference()
                TC_NULL -> readNull()
                TC_STRING -> readString()
                TC_LONGSTRING -> readLongString()
                TC_CLASS -> readNewClass()
                TC_ARRAY -> readNewArray()
                TC_ENUM -> readNewEnum()
                else -> unexpectedByte()
            }

            else -> throw IllegalStateException("Impossible")
        }
    }

    private fun readBlockData(): Serialized.BlockData {
        checkNextByte(TC_BLOCKDATA, "TC_BLOCKDATA")

        val length = data.get().toInt()
        return Serialized.BlockData(
            data.getSlice(length = length)
        ).also {
            data.position(data.position() + length)
        }
    }

    private fun readLongBlockData(): Serialized.BlockData {
        checkNextByte(TC_BLOCKDATALONG, "TC_BLOCKDATALONG")

        val length = data.getInt()
        return Serialized.BlockData(
            data.getSlice(length = length)
        ).also {
            data.position(data.position() + length)
        }
    }

    private fun checkNextByte(byte: Byte, name: String) {
        val next = data.get()
        check(next == byte) { "Error: Illegal value for $name (should be ${byte.toHexString()}" }
    }

    private fun unexpectedByte(): Nothing {
        println(data.getSlice(0, data.position()).toHexString())
        throw IllegalStateException("Error: Unexpected value 0x${data.peek().toHexString()} at offset ${data.position()}")
    }

    companion object {
        private val primitiveCodeMap: Map<Char, (ByteBuffer) -> Any> = buildMap {
            put('Z') { buffer -> buffer.get() == 0.toByte() }
            put('B', ByteBuffer::get)
            put('S', ByteBuffer::getShort)
            put('C', ByteBuffer::getChar)
            put('I', ByteBuffer::getInt)
            put('J', ByteBuffer::getLong)
            put('F', ByteBuffer::getFloat)
            put('D', ByteBuffer::getDouble)
        }
    }
}

/**
 * Get the next byte available without advancing the buffer.
 */
private fun ByteBuffer.peek(): Byte = get(position())

/**
 * Read a string (of [length]) from the buffer using Java serialization's "modified" UTF-8 encoding documented in
 * [java.io.DataInput].
 */
private fun ByteBuffer.readUtf(length: Int = this.getShort().toInt()): String {
    return getSlice(length = length)
        .toUtf8String()
        .also {
            // reposition this buffer to the end of the string
            position(position() + length)
        }
}

/**
 * Copies [length] bytes from [ByteBuffer.position] into a new bytearray.
 */
private fun ByteBuffer.getSlice(offset: Int = position(), length: Int): ByteArray {
    return ByteArray(length).also { array ->
        get(offset, array, 0, length)
    }
}

private fun ByteArray.toUtf8String(): String {
    val output = CharArray(size)

    var c: Int
    var char2: Int
    var char3: Int
    var index = 0
    var charIndex = 0

    while (index < size) {
        c = this[index].toInt() and 0xff
        if (c > 127) {
            break
        }
        index++
        output[charIndex++] = c.toChar()
    }
    while (index < size) {
        c = this[index].toInt() and 0xff
        when (c shr 4) {
            0, 1, 2, 3, 4, 5, 6, 7 -> {
                index++
                output[charIndex++] = c.toChar()
            }

            12, 13 -> {
                index += 2
                if (index > size) {
                    throw UTFDataFormatException("malformed input: partial character at end")
                }
                char2 = this[index - 1].toInt()
                if (char2 and 0xC0 != 0x80) {
                    throw UTFDataFormatException("malformed input around byte $index")
                }
                output[charIndex++] = (c and 0x1F shl 6 or (char2 and 0x3F)).toChar()
            }

            14 -> {
                index += 3
                if (index > size) {
                    throw UTFDataFormatException("malformed input: partial character at end")
                }
                char2 = this[index - 2].toInt()
                char3 = this[index - 1].toInt()
                if (char2 and 0xC0 != 0x80 || char3 and 0xC0 != 0x80) {
                    throw UTFDataFormatException("malformed input around byte ${index - 1}")
                }
                output[charIndex++] = (c and 0x0F shl 12
                    or (char2 and 0x3F shl 6)
                    or (char3 and 0x3F shl 0)
                    ).toChar()
            }

            else -> throw UTFDataFormatException("malformed input around byte $index")
        }
    }

    return String(output, 0, charIndex)
}

@Serializable
sealed interface Serialized {
    @Serializable
    data class Enum(
        val classDesc: ClassDesc,
        val enumConstantName: String,
    ) : Serialized

    @Serializable
    data class UtfString(
        val data: String,
    ) : Serialized

    @Serializable(with = Primitive.Serializer::class)
    data class Primitive(
        val value: Any
    ) : Serialized {
        companion object Serializer : KSerializer<Primitive> {
            private val delegate = MapSerializer(String.serializer(), String.serializer())
            override val descriptor: SerialDescriptor = delegate.descriptor

            override fun deserialize(decoder: Decoder): Primitive {
                TODO("Not yet implemented")
            }

            override fun serialize(encoder: Encoder, value: Primitive) {
                encoder.encodeSerializableValue(delegate, mapOf(
                    "type" to value.value::class.java.simpleName,
                    "value" to value.value.toString()
                ))
            }
        }
    }

    @Serializable
    data class Array(
        val contents: List<Serialized?>
    ) : Serialized

    @Serializable
    data class NewObject(
        val classDesc: List<ClassDesc>,
        val classData: Map<String, List<Serialized?>>
    ) : Serialized

    @Serializable
    data class BlockData(
        @Serializable(with = ByteArraySerializer::class)
        val data: ByteArray
    ) : Serialized {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as BlockData

            return data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }

    @Serializable
    sealed interface ClassDesc : Serialized {
        val superClass: ClassDesc?

        @Serializable
        data class Proxy(
            val proxyInterfaceNames: List<String>,
            override val superClass: ClassDesc?,
        ) : ClassDesc

        @Serializable
        data class Local(
            val name: String,
            val serialVersionUid: Long,
            val writeMethod: Boolean,
            val serializable: Boolean,
            val externalizable: Boolean,
            val blockData: Boolean,
            val fields: List<Field>,
            val annotation: NewObject?,
            override val superClass: ClassDesc?,
        ) : ClassDesc {
            init {
                if (serializable) {
                    check(!externalizable) { "Cannot be serializable and externalizable" }
                    check(!blockData) { "Cannot be serializable and have blockdata" }
                }
                if (externalizable) {
                    check(!writeMethod) { "Cannot be externalizable and have a write method" }
                }
                check(serializable || externalizable) { "Must be either externalizable or serializable" }
            }
        }
    }

    @Serializable
    sealed interface Field : Serialized {
        val fieldName: String
        val typeCode: Char

        @Serializable
        data class PrimitiveField(
            override val fieldName: String,
            override val typeCode: Char
        ) : Field

        @Serializable
        data class ObjectField(
            override val fieldName: String,
            val type: String,
        ) : Field {
            override val typeCode: Char = 'L'
        }

        @Serializable
        data class ArrayField(
            override val fieldName: String,
            val componentType: String,
        ) : Field {
            override val typeCode: Char = '['
        }
    }
}

private val json = Json {
    classDiscriminator = "\$type"
    prettyPrint = true
}

fun main() {
    val mapOf: Map<String, () -> ByteArray> = mapOf(
//        "basicTest" to ::basicTest,
//        "user" to ::userTest,
        "alarms" to ::alarmTest
    )
    for ((name, getter) in mapOf) {
        println(name)
        val data = getter.invoke()
        println(SerializationDumper(data).parseStream())
        for (element in JavaSerializationReader(data)) {
            println(json.encodeToString(element))
        }
    }
}

@OptIn(ExperimentalStdlibApi::class)
fun basicTest(): ByteArray {
    return "aced0005737200044c69737469c88a154016ae6802000249000576616c75654c00046e6578747400064c4c6973743b7870000000117371007e0000000000137071007e0003".hexToByteArray()
}

fun userTest(): ByteArray {
    return Path("/Users/pgriffith/Projects/kindling/user.ser").readBytes()
}

fun alarmTest(): ByteArray {
    return Path("/Users/pgriffith/Projects/kindling/.alarms").readBytes()
}

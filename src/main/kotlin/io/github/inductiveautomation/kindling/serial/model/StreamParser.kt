package io.github.inductiveautomation.kindling.serial.model

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
import java.io.ObjectStreamConstants.TC_EXCEPTION
import java.io.ObjectStreamConstants.TC_LONGSTRING
import java.io.ObjectStreamConstants.TC_NULL
import java.io.ObjectStreamConstants.TC_OBJECT
import java.io.ObjectStreamConstants.TC_PROXYCLASSDESC
import java.io.ObjectStreamConstants.TC_REFERENCE
import java.io.ObjectStreamConstants.TC_RESET
import java.io.ObjectStreamConstants.TC_STRING
import java.io.ObjectStreamConstants.baseWireHandle
import java.nio.ByteBuffer

class StreamParseException(message: String) : Exception(message)

/**
 * Parses a Java serialization stream into a [SerializedStream] tree, without requiring any of the
 * serialized classes on the classpath.
 *
 * Throws [StreamParseException] if the stream header is not a serialization stream at all; failures past the
 * header are captured as [UnparseableNode]s instead, preserving everything parsed up to that point.
 *
 * Originally derived from Nicky Bloor's (@NickstaDB) SerializationDumper.
 */
class StreamParser private constructor(private val data: ByteBuffer) {
    private var handleCursor: Int = baseWireHandle
    private val handles = mutableMapOf<Int, StreamContent>()

    private fun parseStream(): SerializedStream {
        val magic = data.short
        if (magic != STREAM_MAGIC) {
            throw StreamParseException("Invalid STREAM_MAGIC 0x%04x; expected 0xACED".format(magic))
        }
        val version = data.short
        if (version != STREAM_VERSION) {
            throw StreamParseException("Invalid STREAM_VERSION $version; expected $STREAM_VERSION")
        }

        val contents = buildList {
            while (data.hasRemaining()) {
                val start = data.position()
                try {
                    add(readContent())
                } catch (e: Exception) {
                    data.position(start)
                    val remaining = ByteArray(data.remaining()).also(data::get)
                    add(UnparseableNode(start, e.message ?: e.toString(), remaining))
                }
            }
        }
        return SerializedStream(contents)
    }

    private fun readContent(): StreamContent = when (val next = peek()) {
        TC_OBJECT -> readObject()
        TC_CLASS -> readClassNode()
        TC_ARRAY -> readArray()
        TC_STRING, TC_LONGSTRING -> readStringNode()
        TC_ENUM -> readEnum()
        TC_CLASSDESC -> readRegularClassDesc()
        TC_PROXYCLASSDESC -> readProxyClassDesc()
        TC_REFERENCE -> readReference()
        TC_NULL -> readNull()
        TC_BLOCKDATA -> readBlockData()
        TC_BLOCKDATALONG -> readLongBlockData()
        TC_RESET -> {
            expect(TC_RESET, "TC_RESET")
            resetHandles()
            ResetNode
        }
        TC_EXCEPTION -> readException()
        else -> throw StreamParseException(
            "Illegal content element type 0x%02x at offset %d".format(next, data.position()),
        )
    }

    /**
     * TC_OBJECT classDesc newHandle classdata[]
     */
    private fun readObject(): ObjectNode {
        expect(TC_OBJECT, "TC_OBJECT")
        val classDesc = readClassDescRef()
        val handle = nextHandle()
        val classData = readClassData(resolve(classDesc))
        return ObjectNode(classDesc, handle, classData).also { handles[handle] = it }
    }

    /**
     * classDesc: newClassDesc | TC_NULL | TC_REFERENCE
     */
    private fun readClassDescRef(): ClassDescRef? = when (val next = peek()) {
        TC_CLASSDESC -> readRegularClassDesc()
        TC_PROXYCLASSDESC -> readProxyClassDesc()
        TC_REFERENCE -> readReference()
        TC_NULL -> {
            expect(TC_NULL, "TC_NULL")
            null
        }
        else -> throw StreamParseException("Illegal classDesc type 0x%02x at offset %d".format(next, data.position()))
    }

    private fun resolve(ref: ClassDescRef?): ClassDesc? = when (ref) {
        null -> null
        is ClassDesc -> ref
        is ReferenceNode -> handles[ref.handle] as? ClassDesc
            ?: throw StreamParseException("Invalid classDesc reference 0x%08x".format(ref.handle))
    }

    /**
     * TC_CLASSDESC className serialVersionUID newHandle classDescInfo
     */
    private fun readRegularClassDesc(): RegularClassDesc {
        expect(TC_CLASSDESC, "TC_CLASSDESC")
        val name = readUtf()
        val serialVersionUID = data.long
        val handle = nextHandle()

        val flags = data.get()
        validateFlags(flags)
        val fieldCount = data.short.toInt()
        val fields = List(fieldCount) { readFieldDesc() }
        val annotation = readAnnotation()
        val superClass = readClassDescRef()

        return RegularClassDesc(name, serialVersionUID, handle, flags, fields, annotation, superClass)
            .also { handles[handle] = it }
    }

    /**
     * TC_PROXYCLASSDESC newHandle proxyClassDescInfo
     */
    private fun readProxyClassDesc(): ProxyClassDesc {
        expect(TC_PROXYCLASSDESC, "TC_PROXYCLASSDESC")
        val handle = nextHandle()
        val interfaceCount = data.int
        val interfaces = List(interfaceCount) { readUtf() }
        val annotation = readAnnotation()
        val superClass = readClassDescRef()

        return ProxyClassDesc(handle, interfaces, annotation, superClass)
            .also { handles[handle] = it }
    }

    private fun validateFlags(flags: Byte) {
        val serializable = flags hasFlag SC_SERIALIZABLE
        val externalizable = flags hasFlag SC_EXTERNALIZABLE
        when {
            serializable && externalizable -> throw StreamParseException(
                "Illegal classDescFlags; SC_SERIALIZABLE is not compatible with SC_EXTERNALIZABLE",
            )
            serializable && flags hasFlag SC_BLOCK_DATA -> throw StreamParseException(
                "Illegal classDescFlags; SC_SERIALIZABLE is not compatible with SC_BLOCK_DATA",
            )
            externalizable && flags hasFlag SC_WRITE_METHOD -> throw StreamParseException(
                "Illegal classDescFlags; SC_EXTERNALIZABLE is not compatible with SC_WRITE_METHOD",
            )
        }
    }

    /**
     * fieldDesc: prim_typecode fieldName | obj_typecode fieldName className1
     */
    private fun readFieldDesc(): FieldDesc {
        val typeCode = (data.get().toInt() and 0xFF).toChar()
        if (typeCode !in "BCDFIJSZ[L") {
            throw StreamParseException("Illegal field type code '$typeCode' at offset ${data.position() - 1}")
        }
        val name = readUtf()
        val typeName = if (typeCode == 'L' || typeCode == '[') readStringValue() else null
        return FieldDesc(typeCode, name, typeName)
    }

    /**
     * Contents until TC_ENDBLOCKDATA; used for both classAnnotation and objectAnnotation.
     */
    private fun readAnnotation(): List<StreamContent> = buildList {
        while (peek() != TC_ENDBLOCKDATA) {
            add(readContent())
        }
        data.get()
    }

    /**
     * classdata for each class in the inheritance chain, most super class first.
     */
    private fun readClassData(desc: ClassDesc?): List<ClassData> = generateSequence(desc) { resolve(it.superClass) }
        .toList()
        .asReversed()
        .map { readSingleClassData(it) }

    private fun readSingleClassData(desc: ClassDesc): ClassData = when (desc) {
        // proxy class descriptors carry no flags or fields of their own; data comes from the superclass chain
        is ProxyClassDesc -> ClassData("<dynamic proxy>")
        is RegularClassDesc -> when {
            desc.isExternalizable && !desc.hasBlockData -> throw StreamParseException(
                "externalContents of ${desc.name} was written with protocol version 1 and cannot be parsed without the class",
            )
            desc.isExternalizable -> ClassData(desc.name, annotation = readAnnotation())
            else -> {
                val fields = if (desc.isSerializable) {
                    desc.fields.map { FieldData(it.name, readFieldValue(it.typeCode)) }
                } else {
                    emptyList()
                }
                val annotation = if (desc.isSerializable && desc.hasWriteMethod) readAnnotation() else null
                ClassData(desc.name, fields, annotation)
            }
        }
    }

    private fun readFieldValue(typeCode: Char): FieldValue = when (typeCode) {
        'B' -> ByteValue(data.get())
        'C' -> CharValue(data.char)
        'D' -> DoubleValue(data.double)
        'F' -> FloatValue(data.float)
        'I' -> IntValue(data.int)
        'J' -> LongValue(data.long)
        'S' -> ShortValue(data.short)
        'Z' -> BooleanValue(data.get() != 0.toByte())
        '[' -> when (val next = peek()) {
            TC_ARRAY -> readArray()
            TC_REFERENCE -> readReference()
            TC_NULL -> readNull()
            else -> throw StreamParseException(
                "Unexpected array field value type 0x%02x at offset %d".format(next, data.position()),
            )
        }
        'L' -> when (val next = peek()) {
            TC_OBJECT -> readObject()
            TC_REFERENCE -> readReference()
            TC_NULL -> readNull()
            TC_STRING, TC_LONGSTRING -> readStringNode()
            TC_CLASS -> readClassNode()
            TC_ARRAY -> readArray()
            TC_ENUM -> readEnum()
            else -> throw StreamParseException(
                "Unexpected object field value type 0x%02x at offset %d".format(next, data.position()),
            )
        }
        else -> throw StreamParseException("Illegal field type code '$typeCode'")
    }

    /**
     * TC_ARRAY classDesc newHandle (int)size values[size]
     */
    private fun readArray(): ArrayNode {
        expect(TC_ARRAY, "TC_ARRAY")
        val classDesc = readClassDescRef()
        val resolved = resolve(classDesc) as? RegularClassDesc
            ?: throw StreamParseException("Array missing class description")
        if (resolved.name.firstOrNull() != '[') {
            throw StreamParseException("Array class name does not begin with '[': ${resolved.name}")
        }
        val handle = nextHandle()
        val size = data.int
        val elements = List(size) { readFieldValue(resolved.name[1]) }
        return ArrayNode(classDesc, resolved.name, handle, elements).also { handles[handle] = it }
    }

    /**
     * TC_ENUM classDesc newHandle enumConstantName
     */
    private fun readEnum(): EnumNode {
        expect(TC_ENUM, "TC_ENUM")
        val classDesc = readClassDescRef()
        val handle = nextHandle()
        val constantName = readStringValue()
        return EnumNode(classDesc, handle, constantName).also { handles[handle] = it }
    }

    /**
     * TC_CLASS classDesc newHandle
     */
    private fun readClassNode(): ClassNode {
        expect(TC_CLASS, "TC_CLASS")
        val classDesc = readClassDescRef()
        val handle = nextHandle()
        return ClassNode(classDesc, handle).also { handles[handle] = it }
    }

    /**
     * TC_STRING newHandle utf | TC_LONGSTRING newHandle long-utf
     */
    private fun readStringNode(): StringNode {
        val node = when (val next = peek()) {
            TC_STRING -> {
                data.get()
                val handle = nextHandle()
                StringNode(handle, readUtf())
            }
            TC_LONGSTRING -> {
                data.get()
                val handle = nextHandle()
                StringNode(handle, readLongUtf())
            }
            else -> throw StreamParseException(
                "Illegal newString type 0x%02x at offset %d".format(next, data.position()),
            )
        }
        return node.also { handles[it.handle] = it }
    }

    /**
     * Read a newString and resolve it to its value, following back-references.
     * Used in positions where the grammar guarantees a string (enum constant names, field type names).
     */
    private fun readStringValue(): String = when (val next = peek()) {
        TC_STRING, TC_LONGSTRING -> readStringNode().value
        TC_REFERENCE -> {
            val ref = readReference()
            (handles[ref.handle] as? StringNode)?.value
                ?: throw StreamParseException("Invalid string reference 0x%08x".format(ref.handle))
        }
        else -> throw StreamParseException("Illegal newString type 0x%02x at offset %d".format(next, data.position()))
    }

    /**
     * TC_REFERENCE (int)handle
     */
    private fun readReference(): ReferenceNode {
        expect(TC_REFERENCE, "TC_REFERENCE")
        return ReferenceNode(data.int)
    }

    private fun readNull(): NullNode {
        expect(TC_NULL, "TC_NULL")
        return NullNode
    }

    /**
     * TC_BLOCKDATA (unsigned byte)size contents
     */
    private fun readBlockData(): BlockDataNode {
        expect(TC_BLOCKDATA, "TC_BLOCKDATA")
        return BlockDataNode(readBytes(data.get().toInt() and 0xFF))
    }

    /**
     * TC_BLOCKDATALONG (int)size contents
     */
    private fun readLongBlockData(): BlockDataNode {
        expect(TC_BLOCKDATALONG, "TC_BLOCKDATALONG")
        return BlockDataNode(readBytes(data.int))
    }

    /**
     * TC_EXCEPTION reset (Throwable)object reset
     */
    private fun readException(): ExceptionNode {
        expect(TC_EXCEPTION, "TC_EXCEPTION")
        resetHandles()
        val throwable = readContent()
        resetHandles()
        return ExceptionNode(throwable)
    }

    private fun readUtf(): String = decodeModifiedUtf8(readBytes(data.short.toInt() and 0xFFFF))

    private fun readLongUtf(): String {
        val length = data.long
        if (length !in 0..data.remaining()) {
            throw StreamParseException("Illegal long-utf length $length at offset ${data.position() - Long.SIZE_BYTES}")
        }
        return decodeModifiedUtf8(readBytes(length.toInt()))
    }

    private fun readBytes(length: Int): ByteArray {
        if (length < 0 || length > data.remaining()) {
            throw StreamParseException("Illegal data length $length at offset ${data.position()} (${data.remaining()} bytes remain)")
        }
        return ByteArray(length).also(data::get)
    }

    private fun nextHandle(): Int = handleCursor++

    private fun resetHandles() {
        handles.clear()
        handleCursor = baseWireHandle
    }

    private fun peek(): Byte {
        if (!data.hasRemaining()) {
            throw StreamParseException("Unexpected end of stream at offset ${data.position()}")
        }
        return data.get(data.position())
    }

    private fun expect(code: Byte, name: String) {
        val offset = data.position()
        if (!data.hasRemaining()) {
            throw StreamParseException("Unexpected end of stream at offset $offset; expected $name")
        }
        val actual = data.get()
        if (actual != code) {
            throw StreamParseException("Expected $name (0x%02x) but found 0x%02x at offset %d".format(code, actual, offset))
        }
    }

    companion object {
        fun parse(bytes: ByteArray): SerializedStream = StreamParser(ByteBuffer.wrap(bytes)).parseStream()
    }
}

/**
 * Decodes the 'modified UTF-8' encoding used by `DataOutput.writeUTF` and the serialization protocol.
 * Differs from standard UTF-8 in encoding NUL as two bytes and supplementary characters as surrogate pairs (CESU-8).
 * Malformed sequences decode to U+FFFD rather than failing.
 */
private fun decodeModifiedUtf8(bytes: ByteArray): String = buildString(bytes.size) {
    var i = 0
    while (i < bytes.size) {
        val a = bytes[i].toInt() and 0xFF
        when {
            a and 0x80 == 0 -> {
                append(a.toChar())
                i += 1
            }
            a and 0xE0 == 0xC0 && i + 1 < bytes.size -> {
                val b = bytes[i + 1].toInt() and 0x3F
                append((((a and 0x1F) shl 6) or b).toChar())
                i += 2
            }
            a and 0xF0 == 0xE0 && i + 2 < bytes.size -> {
                val b = bytes[i + 1].toInt() and 0x3F
                val c = bytes[i + 2].toInt() and 0x3F
                append((((a and 0x0F) shl 12) or (b shl 6) or c).toChar())
                i += 3
            }
            else -> {
                append('�')
                i += 1
            }
        }
    }
}

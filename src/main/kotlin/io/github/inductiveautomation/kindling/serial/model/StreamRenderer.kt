package io.github.inductiveautomation.kindling.serial.model

import java.util.HexFormat

private val HEX = HexFormat.of()

/** Maximum number of bytes of unparseable data to include in a dump before truncating. */
private const val MAX_RAW_DUMP = 512

/**
 * Renders this stream as an indented, human-readable text dump, in the spirit of NickstaDB's SerializationDumper.
 */
fun SerializedStream.toDump(): String = buildString {
    line(0, "STREAM_MAGIC - 0xaced")
    line(0, "STREAM_VERSION - 5")
    for (content in contents) {
        appendNode(content, 0)
    }
}

private fun StringBuilder.line(depth: Int, text: String) {
    repeat(depth) { append("  ") }
    append(text)
    append('\n')
}

private fun hex(handle: Int): String = "0x%08x".format(handle)

private fun StringBuilder.appendNode(node: StreamContent, depth: Int) {
    when (node) {
        is ObjectNode -> {
            line(depth, "TC_OBJECT (handle ${hex(node.handle)})")
            appendClassDescRef("classDesc", node.classDesc, depth + 1)
            line(depth + 1, "classdata")
            for (classData in node.classData) {
                appendClassData(classData, depth + 2)
            }
        }
        is RegularClassDesc -> {
            line(depth, "TC_CLASSDESC ${node.name} (handle ${hex(node.handle)})")
            line(depth + 1, "serialVersionUID - 0x%016x".format(node.serialVersionUID))
            line(depth + 1, "flags - 0x%02x - %s".format(node.flags, node.flagNames()))
            if (node.fields.isNotEmpty()) {
                line(depth + 1, "fields (${node.fields.size})")
                for (field in node.fields) {
                    line(depth + 2, field.describe())
                }
            }
            if (node.annotation.isNotEmpty()) {
                line(depth + 1, "classAnnotations")
                for (annotation in node.annotation) {
                    appendNode(annotation, depth + 2)
                }
            }
            appendClassDescRef("superClassDesc", node.superClass, depth + 1)
        }
        is ProxyClassDesc -> {
            line(depth, "TC_PROXYCLASSDESC (handle ${hex(node.handle)})")
            line(depth + 1, "interfaces - ${node.interfaces.joinToString()}")
            if (node.annotation.isNotEmpty()) {
                line(depth + 1, "classAnnotations")
                for (annotation in node.annotation) {
                    appendNode(annotation, depth + 2)
                }
            }
            appendClassDescRef("superClassDesc", node.superClass, depth + 1)
        }
        is ClassNode -> {
            line(depth, "TC_CLASS (handle ${hex(node.handle)})")
            appendClassDescRef("classDesc", node.classDesc, depth + 1)
        }
        is ArrayNode -> {
            line(depth, "TC_ARRAY ${node.className} (handle ${hex(node.handle)}, length ${node.elements.size})")
            appendClassDescRef("classDesc", node.classDesc, depth + 1)
            if (node.className == "[B") {
                val bytes = node.elements.filterIsInstance<ByteValue>().map { it.value }.toByteArray()
                line(depth + 1, "bytes - 0x${HEX.formatHex(bytes)}")
            } else {
                node.elements.forEachIndexed { index, element ->
                    appendFieldValue(index.toString(), element, depth + 1)
                }
            }
        }
        is StringNode -> line(depth, "TC_STRING \"${node.value}\" (handle ${hex(node.handle)})")
        is EnumNode -> {
            line(depth, "TC_ENUM ${node.constantName} (handle ${hex(node.handle)})")
            appendClassDescRef("classDesc", node.classDesc, depth + 1)
        }
        is ReferenceNode -> line(depth, "TC_REFERENCE (handle ${hex(node.handle)})")
        is NullNode -> line(depth, "TC_NULL")
        is BlockDataNode -> line(depth, "TC_BLOCKDATA (${node.data.size} bytes) 0x${HEX.formatHex(node.data)}")
        is ExceptionNode -> {
            line(depth, "TC_EXCEPTION")
            appendNode(node.throwable, depth + 1)
        }
        is ResetNode -> line(depth, "TC_RESET")
        is UnparseableNode -> {
            line(depth, "!! unparseable data at offset ${node.offset} - ${node.message}")
            val shown = node.bytes.copyOf(minOf(node.bytes.size, MAX_RAW_DUMP))
            val suffix = if (node.bytes.size > MAX_RAW_DUMP) "… (${node.bytes.size} bytes total)" else ""
            line(depth + 1, "0x${HEX.formatHex(shown)}$suffix")
        }
    }
}

private fun StringBuilder.appendClassDescRef(label: String, ref: ClassDescRef?, depth: Int) {
    when (ref) {
        null -> line(depth, "$label - TC_NULL")
        is ReferenceNode -> line(depth, "$label - TC_REFERENCE (handle ${hex(ref.handle)})")
        is ClassDesc -> {
            line(depth, label)
            appendNode(ref, depth + 1)
        }
    }
}

private fun StringBuilder.appendClassData(classData: ClassData, depth: Int) {
    line(depth, classData.className)
    if (classData.fields.isNotEmpty()) {
        line(depth + 1, "values")
        for (field in classData.fields) {
            appendFieldValue(field.name, field.value, depth + 2)
        }
    }
    classData.annotation?.let { annotation ->
        line(depth + 1, "objectAnnotation")
        for (content in annotation) {
            appendNode(content, depth + 2)
        }
    }
}

private fun StringBuilder.appendFieldValue(label: String, value: FieldValue, depth: Int) {
    when (value) {
        is PrimitiveValue -> line(depth, "$label - ${value.describe()}")
        is NullNode -> line(depth, "$label - TC_NULL")
        is ReferenceNode -> line(depth, "$label - TC_REFERENCE (handle ${hex(value.handle)})")
        is StringNode -> line(depth, "$label - TC_STRING \"${value.value}\" (handle ${hex(value.handle)})")
        is StreamContent -> {
            line(depth, label)
            appendNode(value, depth + 1)
        }
    }
}

private fun PrimitiveValue.describe(): String = when (this) {
    is ByteValue -> "(byte) $value"
    is CharValue -> "(char) '$value'"
    is DoubleValue -> "(double) $value"
    is FloatValue -> "(float) $value"
    is IntValue -> "(int) $value"
    is LongValue -> "(long) $value"
    is ShortValue -> "(short) $value"
    is BooleanValue -> "(boolean) $value"
}

private fun FieldDesc.describe(): String {
    val type = when (typeCode) {
        'B' -> "byte"
        'C' -> "char"
        'D' -> "double"
        'F' -> "float"
        'I' -> "int"
        'J' -> "long"
        'S' -> "short"
        'Z' -> "boolean"
        else -> typeName ?: typeCode.toString()
    }
    return "$type $name"
}

private fun RegularClassDesc.flagNames(): String = buildList {
    if (hasWriteMethod) add("SC_WRITE_METHOD")
    if (isSerializable) add("SC_SERIALIZABLE")
    if (isExternalizable) add("SC_EXTERNALIZABLE")
    if (hasBlockData) add("SC_BLOCK_DATA")
    if (isEnum) add("SC_ENUM")
}.joinToString(" | ")

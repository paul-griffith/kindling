package io.github.inductiveautomation.kindling.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.sql.Date
import java.sql.JDBCType
import java.sql.Time
import java.sql.Timestamp
import java.util.Properties
import java.util.ServiceLoader
import kotlin.math.log2
import kotlin.math.pow
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun String.truncate(length: Int = 20): String {
    return asIterable().joinToString(separator = "", limit = length)
}

inline fun <reified T> getLogger(): Logger {
    return LoggerFactory.getLogger(T::class.java.name)
}

inline fun StringBuilder.tag(
    tag: String,
    content: StringBuilder.() -> Unit,
) {
    append("<").append(tag).append(">")
    content(this)
    append("</").append(tag).append(">")
}

fun StringBuilder.tag(
    tag: String,
    content: String,
) {
    tag(tag) { append(content) }
}

/**
 * Returns the mode (most common value) in a Grouping<T>
 */
fun <T> Grouping<T, Int>.mode(): Int? = eachCount().maxOfOrNull { it.key }

fun <T, U : Comparable<U>> List<T>.isSortedBy(keyFn: (T) -> U): Boolean {
    return asSequence().zipWithNext { a, b ->
        keyFn(a) <= keyFn(b)
    }.all { it }
}

val JDBCType.javaType: Class<*>
    get() =
        when (this) {
            JDBCType.BIT -> Boolean::class
            JDBCType.TINYINT -> Short::class
            JDBCType.SMALLINT -> Short::class
            JDBCType.INTEGER -> Int::class
            JDBCType.BIGINT -> Long::class
            JDBCType.FLOAT -> Float::class
            JDBCType.REAL -> Double::class
            JDBCType.DOUBLE -> Double::class
            JDBCType.NUMERIC -> BigDecimal::class
            JDBCType.DECIMAL -> BigDecimal::class
            JDBCType.CHAR -> String::class
            JDBCType.VARCHAR -> String::class
            JDBCType.LONGVARCHAR -> String::class
            JDBCType.DATE -> Date::class
            JDBCType.TIME -> Time::class
            JDBCType.TIMESTAMP -> Timestamp::class
            JDBCType.BINARY -> ByteArray::class
            JDBCType.VARBINARY -> ByteArray::class
            JDBCType.LONGVARBINARY -> ByteArray::class
            JDBCType.BOOLEAN -> Boolean::class
            JDBCType.ROWID -> Long::class
            JDBCType.NCHAR -> String::class
            JDBCType.NVARCHAR -> String::class
            JDBCType.LONGNVARCHAR -> String::class
            JDBCType.BLOB -> ByteArray::class
            JDBCType.CLOB -> ByteArray::class
            JDBCType.NCLOB -> ByteArray::class
            else -> Any::class
        }.javaObjectType

/**
 * Creates and returns a new [Properties], loading keys from [inputStream] according to the loading strategy specified
 * via [loader], e.g. [Properties.load] (the default) or [Properties.loadFromXML].
 * The default loader closes [inputStream].
 */
fun Properties(
    inputStream: InputStream,
    loader: Properties.(InputStream) -> Unit = { stream -> stream.use(::load) },
): Properties = Properties().apply { loader(inputStream) }

private val prefix = arrayOf("", "k", "m", "g", "t", "p", "e", "z", "y")

fun Long.toFileSizeLabel(): String =
    when {
        this == 0L -> "0B"
        else -> {
            val digits = log2(toDouble()).toInt() / 10
            val precision = digits.coerceIn(0, 2)
            "%,.${precision}f${prefix[digits]}b".format(toDouble() / 2.0.pow(digits * 10.0))
        }
    }

operator fun MatchGroupCollection.getValue(
    thisRef: Any?,
    property: KProperty<*>,
): MatchGroup {
    return requireNotNull(get(property.name))
}

inline fun <reified S> loadService(): ServiceLoader<S> {
    return ServiceLoader.load(S::class.java)
}

fun String.escapeHtml(): String {
    return buildString {
        for (char in this@escapeHtml) {
            when (char) {
                '>' -> append("&gt;")
                '<' -> append("&lt;")
                else -> append(char)
            }
        }
    }
}

fun debounce(
    waitTime: Duration = 300.milliseconds,
    coroutineScope: CoroutineScope,
    destinationFunction: () -> Unit,
): () -> Unit {
    var debounceJob: Job? = null
    return {
        debounceJob?.cancel()
        debounceJob =
            coroutineScope.launch {
                delay(waitTime)
                destinationFunction()
            }
    }
}

/**
 * Transfers [this] to [output], closing both streams.
 */
infix fun InputStream.transferTo(output: OutputStream) {
    this.use { input ->
        output.use(input::transferTo)
    }
}

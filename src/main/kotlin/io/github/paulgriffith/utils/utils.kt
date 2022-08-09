package io.github.paulgriffith.utils

import io.github.evanrupert.excelkt.workbook
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteDataSource
import java.io.File
import java.math.BigDecimal
import java.nio.file.Path
import java.sql.Connection
import java.sql.Date
import java.sql.JDBCType
import java.sql.ResultSet
import java.sql.Time
import java.sql.Timestamp
import javax.swing.table.TableModel
import kotlin.math.log2
import kotlin.math.pow
import kotlin.reflect.KProperty

fun String.truncate(length: Int = 20): String {
    return asIterable().joinToString(separator = "", limit = length)
}

inline fun <reified T> getLogger(): Logger {
    return LoggerFactory.getLogger(T::class.java.name)
}

/**
 * Exhausts (and closes) a ResultSet into a list using [transform].
 */
fun <T> ResultSet.toList(
    transform: (ResultSet) -> T,
): List<T> {
    return use { rs ->
        buildList {
            while (rs.next()) {
                add(transform(rs))
            }
        }
    }
}

val JDBCType.javaType: Class<*>
    get() = when (this) {
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

fun SQLiteConnection(path: Path): Connection {
    return SQLiteDataSource().apply {
        url = "jdbc:sqlite:file:$path"
        setReadOnly(true)
    }.connection
}

private val prefix = arrayOf("", "k", "m", "g", "t", "p", "e", "z", "y")

fun Long.toFileSizeLabel(): String = when {
    this == 0L -> "0B"
    else -> {
        val digits = log2(toDouble()).toInt() / 10
        val precision = when (digits) {
            0 -> 0
            1 -> 1
            else -> 2
        }
        "%,.${precision}f${prefix[digits]}b".format(toDouble() / 2.0.pow(digits * 10.0))
    }
}

operator fun MatchGroupCollection.getValue(thisRef: Any?, property: KProperty<*>): MatchGroup {
    return requireNotNull(get(property.name))
}

fun TableModel.exportToCSV(file: File) {
    file.printWriter().use { out ->
        (0 until columnCount).joinTo(buffer = out, separator = ",") { col ->
            getColumnName(col)
        }
        out.println()
        (0 until rowCount).forEach { row ->
            (0 until columnCount).joinTo(buffer = out, separator = ",") { col ->
                getValueAt(row, col)?.toString().orEmpty()
            }
            out.println()
        }
    }
}

fun TableModel.exportToXLSX(file: File) = file.outputStream().use { fos ->
    workbook {
        sheet("Sheet 1") { // TODO: Some way to pipe in a more useful sheet name (or multiple sheets?)
            row {
                (0 until columnCount).forEach { col ->
                    cell(getColumnName(col))
                }
            }
            (0 until rowCount).forEach { row ->
                row {
                    (0 until columnCount).forEach { col ->
                        when (val value = getValueAt(row, col)) {
                            is Double -> cell(
                                value,
                                createCellStyle {
                                    dataFormat = xssfWorkbook.createDataFormat().getFormat("0.00")
                                }
                            )
                            else -> cell(value ?: "")
                        }
                    }
                }
            }
        }
    }.xssfWorkbook.write(fos)
}

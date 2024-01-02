package io.github.inductiveautomation.kindling.utils

import io.github.inductiveautomation.kindling.core.Kindling.Preferences.Experimental.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteDataSource
import java.io.InputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.nio.file.Path
import java.sql.Connection
import java.sql.Date
import java.sql.JDBCType
import java.sql.ResultSet
import java.sql.Time
import java.sql.Timestamp
import java.util.Properties
import java.util.ServiceLoader
import javax.swing.JOptionPane
import javax.swing.table.TableModel
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

inline fun StringBuilder.tag(tag: String, content: StringBuilder.() -> Unit) {
    append("<").append(tag).append(">")
    content(this)
    append("</").append(tag).append(">")
}

fun StringBuilder.tag(tag: String, content: String) {
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

fun SQLiteConnection(path: Path, readOnly: Boolean = true): Connection {
    return SQLiteDataSource().apply {
        url = "jdbc:sqlite:file:$path"
        setReadOnly(readOnly)
    }.connection
}

fun Properties(inputStream: InputStream): Properties = Properties().apply { load(inputStream) }

private val prefix = arrayOf("", "k", "m", "g", "t", "p", "e", "z", "y")

fun Long.toFileSizeLabel(): String = when {
    this == 0L -> "0B"
    else -> {
        val digits = log2(toDouble()).toInt() / 10
        val precision = digits.coerceIn(0, 2)
        "%,.${precision}f${prefix[digits]}b".format(toDouble() / 2.0.pow(digits * 10.0))
    }
}

operator fun MatchGroupCollection.getValue(thisRef: Any?, property: KProperty<*>): MatchGroup {
    return requireNotNull(get(property.name))
}

fun TableModel.toCSV(appendable: Appendable) {
    (0 until columnCount).joinTo(buffer = appendable, separator = ",") { col ->
        getColumnName(col)
    }
    appendable.appendLine()
    (0 until rowCount).forEach { row ->
        (0 until columnCount).joinTo(buffer = appendable, separator = ",") { col ->
            """"${getValueAt(row, col)?.toString().orEmpty()}""""
        }
        appendable.appendLine()
    }
}

private const val UPLOAD_URL =
    "https://iazendesk.inductiveautomation.com/system/webdev/ThreadCSVImportTool/upload_thread_dump"

private val uploadClient = HttpClient(CIO)

private suspend fun HttpClient.checkFileAndUser(
    filename: String,
    username: String,
): Pair<HttpStatusCode, Boolean> {
    val response = get(UPLOAD_URL) {
        url {
            parameter("filename", filename)
            parameter("username", username)
        }
    }
    return Pair(response.status, response.bodyAsText().toBoolean())
}

suspend fun HttpClient.upload(model: TableModel, filename: String, username: String): Boolean {
    val httpResponse = post(UPLOAD_URL) {
        url {
            parameter("username", username)
            parameter("filename", filename)
        }
        contentType(ContentType.Text.CSV)
        setBody(buildString { model.toCSV(this) })
    }
    return httpResponse.body<String>().toBoolean()
}

fun TableModel.uploadToWeb(filename: String) {
    val username = User.currentValue.ifEmpty {
        JOptionPane.showInputDialog(null, "Enter Username:\n")
    }
    if (!username.isNullOrEmpty()) {
        CoroutineScope(Dispatchers.IO).launch {
            User.currentValue = username
            val responseData = uploadClient.checkFileAndUser(filename, username)
            val uploadExists = responseData.second
            val responseCode = responseData.first

            EDT_SCOPE.launch {
                if (responseCode.value in 200..299) {
                    if (!uploadExists || JOptionPane.showConfirmDialog(
                            null,
                            "The filename for this thread already exists in the database. Would you like to overwrite it?",
                            "Filename Already Exists",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                        ) == JOptionPane.YES_OPTION
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val uploaded = uploadClient.upload(this@uploadToWeb, filename, username)

                            EDT_SCOPE.launch {
                                if (uploaded) {
                                    JOptionPane.showMessageDialog(
                                        null,
                                        "Uploaded $filename successfully",
                                        "Success",
                                        JOptionPane.INFORMATION_MESSAGE,
                                    )
                                } else {
                                    JOptionPane.showMessageDialog(
                                        null,
                                        "Failed to upload $filename",
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    User.currentValue = ""
                    JOptionPane.showMessageDialog(
                        null,
                        "Failed to upload $filename.\nError response: $responseCode",
                        "Error",
                        JOptionPane.ERROR_MESSAGE,
                    )
                }
            }
        }
    }
}

fun uploadMultipleToWeb(namesAndModels: List<Pair<String, TableModel>>) {
    val fileNames = namesAndModels.map { it.first }

    val username = User.currentValue.ifEmpty {
        JOptionPane.showInputDialog(null, "Enter Username:\n")
    }

    if (!username.isNullOrEmpty()) {
        User.currentValue = username

        CoroutineScope(Dispatchers.IO).launch {
            val responseData = fileNames.map { uploadClient.checkFileAndUser(it, username) }

            fun invalidCode(res: Pair<HttpStatusCode, Boolean>) = res.first.value !in 200..299

            EDT_SCOPE.launch {
                if (responseData.any(::invalidCode)) {
                    User.currentValue = ""
                    val codes = responseData.joinToString("\n") { it.first.toString() }
                    JOptionPane.showMessageDialog(
                        null,
                        "Failed to upload. Received the following response codes:\n$codes",
                        "Error",
                        JOptionPane.ERROR_MESSAGE,
                    )
                } else {
                    val indicesOfExisting = responseData.mapIndexedNotNull { index, (_, exists) ->
                        if (exists) index else null
                    }

                    val overwriteUploadList = indicesOfExisting.joinToString("\n") { i ->
                        val fileName = fileNames[i]
                        "${i + 1}: $fileName"
                    }

                    if (indicesOfExisting.isEmpty() || JOptionPane.showConfirmDialog(
                            null,
                            "The following filename(s) already exist in the database. Overwrite?\n$overwriteUploadList",
                            "Filename Already Exists",
                            JOptionPane.YES_NO_CANCEL_OPTION,
                        ) == JOptionPane.YES_OPTION
                    ) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val (uploadSuccess, uploadFailed) = namesAndModels.partition {
                                uploadClient.upload(it.second, it.first, username)
                            }

                            val failedFileNames = uploadFailed.map { it.first }

                            EDT_SCOPE.launch {
                                if (uploadSuccess.isNotEmpty()) {
                                    JOptionPane.showMessageDialog(
                                        null,
                                        "Uploaded ${uploadSuccess.size} file(s) successfully",
                                        "Success",
                                        JOptionPane.INFORMATION_MESSAGE,
                                    )
                                }
                                if (uploadFailed.isNotEmpty()) {
                                    User.currentValue = ""
                                    JOptionPane.showMessageDialog(
                                        null,
                                        "Failed to upload the following:\n${failedFileNames.joinToString("\n")}",
                                        "Error",
                                        JOptionPane.ERROR_MESSAGE,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        User.currentValue = ""
    }
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
        debounceJob = coroutineScope.launch {
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

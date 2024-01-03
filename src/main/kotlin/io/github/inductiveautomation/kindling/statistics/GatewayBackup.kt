package io.github.inductiveautomation.kindling.statistics

import io.github.inductiveautomation.kindling.utils.SQLiteConnection
import java.io.InputStream
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.sql.Connection
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.fileSize
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

@Suppress("unused", "memberVisibilityCanBePrivate")
class GatewayBackup(path: Path) {
    private val zipFile = FileSystems.newFileSystem(path)
    private val provider: FileSystemProvider = zipFile.provider()
    val root: Path = zipFile.rootDirectories.first()

    val size = path.fileSize()

    val configIDB: Connection by lazy {
        SQLiteConnection(
            Files.createTempFile("gwbk-stats", "idb").also { tempFile ->
                provider.newInputStream(root.resolve(IDB))
                    .use { inputStream ->
                        tempFile.outputStream().use { outputStream ->
                            inputStream.transferTo(outputStream)
                        }
                    }
            }
        )
    }

    val backupInfo: InputStream
        get() = root.resolve(BACKUP_INFO)
            .inputStream()

    val redundancyInfo: InputStream
        get() = root.resolve(REDUNDANCY)
            .inputStream()

    val ignitionConf: InputStream
        get() = root.resolve(IGNITION_CONF)
            .inputStream()

    val logbackXml: InputStream
        get() = root.resolve(LOGBACK)
            .inputStream()

    val gatewayXml: InputStream
        get() = root.resolve(GATEWAY)
            .inputStream()

    companion object {
        const val IDB = "db_backup_sqlite.idb"
        const val BACKUP_INFO = "backupinfo.xml"
        const val REDUNDANCY = "redundancy.xml"
        const val LOGBACK = "logback.xml"
        const val IGNITION_CONF = "ignition.conf"
        const val GATEWAY = "gateway.xml"

        val XML_FACTORY: DocumentBuilderFactory = DocumentBuilderFactory.newDefaultInstance().apply {
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
    }
}

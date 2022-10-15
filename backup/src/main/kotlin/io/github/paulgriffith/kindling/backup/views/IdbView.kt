package io.github.paulgriffith.kindling.backup.views

import io.github.paulgriffith.kindling.idb.generic.GenericView
import io.github.paulgriffith.kindling.utils.SQLiteConnection
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.zip.ZipException
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.io.path.name
import kotlin.io.path.outputStream

class IdbView(provider: FileSystemProvider, path: Path) : JPanel(MigLayout("ins 0, fill")) {
    init {
        val dbTempFile = Files.createTempFile("kindling", path.name)
        try {
            provider.newInputStream(path).use { idb ->
                dbTempFile.outputStream().use(idb::copyTo)
            }
            val connection = SQLiteConnection(dbTempFile)
            val idbView = GenericView(connection)
            add(idbView, "push, grow")
        } catch (e: ZipException) {
            add(JLabel("Unable to open $path; ${e.message}"), BorderLayout.CENTER)
        }
    }
}

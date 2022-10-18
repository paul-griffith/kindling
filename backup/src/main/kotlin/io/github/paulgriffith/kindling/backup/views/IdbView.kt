package io.github.paulgriffith.kindling.backup.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.backup.PathView
import io.github.paulgriffith.kindling.idb.generic.GenericView
import io.github.paulgriffith.kindling.utils.SQLiteConnection
import java.awt.BorderLayout
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.zip.ZipException
import javax.swing.JLabel
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.outputStream

class IdbView(override val provider: FileSystemProvider, override val path: Path) : PathView() {
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

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-hdd.svg")

    companion object {
        fun isIdbFile(path: Path) = path.extension == "idb"
    }
}

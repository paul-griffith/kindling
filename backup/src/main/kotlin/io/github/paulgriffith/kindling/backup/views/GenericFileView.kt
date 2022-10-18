package io.github.paulgriffith.kindling.backup.views

import io.github.paulgriffith.kindling.backup.PathView
import io.github.paulgriffith.kindling.utils.FlatScrollPane
import java.awt.Font
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import java.util.HexFormat
import javax.swing.JTextArea

class GenericFileView(override val provider: FileSystemProvider, override val path: Path) : PathView() {
    private val textArea = JTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    init {
        provider.newInputStream(path).use { file ->
            textArea.text = sequence {
                val window = 16
                val buffer = ByteArray(window)
                var offset = 0
                var read: Int
                do {
                    read = file.readNBytes(buffer, 0, window)

                    // the last read might not be complete, so there could be stale data in the buffer
                    val toRead = buffer.sliceArray(0 until read)
                    val hexBytes = HEX_FORMAT.formatHex(toRead)
                    val decodedBytes = String(
                        CharArray(toRead.size) { i ->
                            val byte = buffer[i]
                            if (byte >= 0 && !Character.isISOControl(byte.toInt())) {
                                Char(byte.toUShort())
                            } else {
                                '.'
                            }
                        },
                    )
                    yield("${hexBytes.padEnd(47)}  $decodedBytes")
                    offset += window
                } while (read == window)
            }.joinToString(separator = "\n")
        }

        add(FlatScrollPane(textArea), "push, grow")
    }

    companion object {
        private val HEX_FORMAT: HexFormat = HexFormat.of().withDelimiter(" ")
    }
}

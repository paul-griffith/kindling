package io.github.paulgriffith.kindling.backup.views

import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JLabel
import kotlin.io.path.name

class ImageView(zip: FileSystemProvider, private val path: Path) : JLabel() {
    init {
        val imageInputStream = ImageIO.createImageInputStream(zip.newInputStream(path))
        val image = imageInputStream.use { iis ->
            val reader = ImageIO.getImageReaders(iis).asSequence().first()
            reader.input = iis
            reader.read(0)
        }

        icon = ImageIcon(image)
    }

    override fun toString() = "ImageView(${path.name})"

    companion object {
        val KNOWN_EXTENSIONS = setOf(
            "png",
            "bmp",
            "gif",
            "jpg",
            "jpeg",
        )
    }
}

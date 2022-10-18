package io.github.paulgriffith.kindling.backup.views

import com.formdev.flatlaf.extras.FlatSVGIcon
import io.github.paulgriffith.kindling.backup.PathView
import java.nio.file.Path
import java.nio.file.spi.FileSystemProvider
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.SwingConstants.CENTER
import kotlin.io.path.extension

class ImageView(override val provider: FileSystemProvider, override val path: Path) : PathView() {
    init {
        val imageInputStream = ImageIO.createImageInputStream(provider.newInputStream(path))
        val image = imageInputStream.use { iis ->
            val reader = ImageIO.getImageReaders(iis).asSequence().first()
            reader.input = iis
            reader.read(0)
        }

        add(
            JLabel().apply {
                horizontalAlignment = CENTER
                verticalAlignment = CENTER
                icon = ImageIcon(image)
            },
            "center",
        )
    }

    override val icon: FlatSVGIcon = FlatSVGIcon("icons/bx-image.svg")

    companion object {
        private val KNOWN_EXTENSIONS = setOf(
            "png",
            "bmp",
            "gif",
            "jpg",
            "jpeg",
        )

        fun isImageFile(path: Path) = path.extension in KNOWN_EXTENSIONS
    }
}

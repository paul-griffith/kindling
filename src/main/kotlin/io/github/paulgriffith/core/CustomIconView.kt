package io.github.paulgriffith.core

import io.github.paulgriffith.utils.Tool
import java.io.File
import javax.swing.Icon
import javax.swing.filechooser.FileView

class CustomIconView : FileView() {
    override fun getIcon(file: File): Icon? {
        return if (file.isFile) {
            Tool.getOrNull(file)?.icon?.derive(16, 16)
        } else {
            null
        }
    }
}

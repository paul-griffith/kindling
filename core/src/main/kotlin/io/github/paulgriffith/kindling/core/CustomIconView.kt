package io.github.paulgriffith.kindling.core

import java.io.File
import javax.swing.Icon
import javax.swing.filechooser.FileView

class CustomIconView : FileView() {
    override fun getIcon(file: File): Icon? = if (file.isFile) {
        Tool.byExtension[file.extension]?.icon?.derive(16, 16)
    } else {
        null
    }

    override fun getTypeDescription(file: File) = if (file.isFile) {
        Tool.byExtension[file.extension]?.description
    } else {
        null
    }
}

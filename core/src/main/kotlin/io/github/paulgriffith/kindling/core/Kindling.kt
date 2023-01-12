package io.github.paulgriffith.kindling.core

import java.awt.Image
import java.awt.Toolkit
import java.io.File
import kotlin.io.path.Path

object Kindling {
    val homeLocation: File = Path(System.getProperty("user.home"), "Downloads").toFile()

    val frameIcon: Image = run {
        val toolkit = Toolkit.getDefaultToolkit()
        toolkit.getImage(this::class.java.getResource("/icons/kindling.png"))
    }
}

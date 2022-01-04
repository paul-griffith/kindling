package io.github.paulgriffith.utils

import net.miginfocom.swing.MigLayout
import java.nio.file.Path
import javax.swing.Icon
import javax.swing.JPanel

abstract class ToolPanel(
    layoutConstraints: String = "ins 0, fill, hidemode 3",
) : JPanel(MigLayout(layoutConstraints)) {
    abstract val path: Path
    abstract val icon: Icon
}

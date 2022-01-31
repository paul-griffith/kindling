package io.github.paulgriffith.utils

import org.jdesktop.swingx.table.TableColumnExt
import javax.swing.table.TableModel

data class Column<R, C>(
    val header: String,
    val getValue: (row: R) -> C,
    val columnCustomization: (TableColumnExt.(model: TableModel) -> Unit)?,
    val clazz: Class<C>,
)

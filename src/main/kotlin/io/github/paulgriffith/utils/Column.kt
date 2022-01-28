package io.github.paulgriffith.utils

import javax.swing.JTable
import javax.swing.table.TableColumn

data class Column<R, C>(
    val header: String,
    val getValue: (row: R) -> C,
    val columnCustomization: (TableColumn.(table: JTable) -> Unit)?,
    val clazz: Class<C>,
)

package io.github.paulgriffith.utils

import javax.swing.table.TableColumn

data class Column<R, C>(
    val header: String,
    val getValue: (row: R) -> C,
    val columnCustomization: (TableColumn.() -> Unit)?,
    val clazz: Class<C>,
)

package io.github.paulgriffith.utils

data class Column<R, C>(
    val header: String,
    val getValue: (row: R) -> C,
    val clazz: Class<C>,
)

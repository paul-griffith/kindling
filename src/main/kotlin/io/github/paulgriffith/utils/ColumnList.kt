package io.github.paulgriffith.utils

import javax.swing.JTable
import javax.swing.table.TableColumn
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

abstract class ColumnList<R> private constructor(
    @PublishedApi internal val list: MutableList<Column<R, *>>,
) : List<Column<R, *>> by list {
    constructor() : this(mutableListOf())

    /**
     * Defines a new column (type T). Uses the name of the property if [name] isn't provided.
     */
    // This is some real Kotlin 'magic', but makes it very easy to define JTable models that can be used type-safely
    protected inline fun <reified T> column(
        name: String? = null,
        noinline column: (TableColumn.() -> Unit)? = null,
        noinline value: (R) -> T,
    ): PropertyDelegateProvider<ColumnList<R>, ReadOnlyProperty<ColumnList<R>, Column<R, T>>> {
        return PropertyDelegateProvider { thisRef, prop ->
            val actual = Column(
                header = name ?: prop.name,
                getValue = value,
                columnCustomization = column,
                clazz = T::class.java,
            )
            thisRef.list.add(actual)
            ReadOnlyProperty { _, _ -> actual }
        }
    }

    operator fun get(column: Column<R, *>): Int = indexOf(column)
}

fun JTable.setupColumns(columns: ColumnList<*>) {
    columns.forEachIndexed { i, column ->
        val tableColumn = TableColumn(i)
        if (column.columnCustomization != null) {
            tableColumn.apply(column.columnCustomization)
        }
        addColumn(tableColumn)
    }
}

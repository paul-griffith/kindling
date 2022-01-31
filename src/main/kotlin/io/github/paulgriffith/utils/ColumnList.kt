package io.github.paulgriffith.utils

import org.jdesktop.swingx.JXTable
import org.jdesktop.swingx.table.ColumnFactory
import org.jdesktop.swingx.table.TableColumnExt
import javax.swing.table.TableModel
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
        noinline column: (TableColumnExt.(model: TableModel) -> Unit)? = null,
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

fun JXTable.installColumnFactory(columns: ColumnList<*>) {
    columnFactory = object : ColumnFactory() {
        override fun configureTableColumn(model: TableModel, columnExt: TableColumnExt) {
            super.configureTableColumn(model, columnExt)
            val column = columns[columnExt.modelIndex]
            columnExt.toolTipText = column.header
            column.columnCustomization?.invoke(columnExt, model)
        }
    }
    createDefaultColumnsFromModel()
}

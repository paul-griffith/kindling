package io.github.paulgriffith.utils

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
        noinline getter: (R) -> T,
    ): PropertyDelegateProvider<ColumnList<R>, ReadOnlyProperty<ColumnList<R>, Column<R, T>>> {
        return PropertyDelegateProvider { thisRef, prop ->
            val column = Column(
                header = name ?: prop.name,
                getValue = getter,
                clazz = T::class.java,
            )
            thisRef.list.add(column)
            ReadOnlyProperty { _, _ -> column }
        }
    }

    operator fun get(column: Column<R, *>): Int = indexOf(column)
}

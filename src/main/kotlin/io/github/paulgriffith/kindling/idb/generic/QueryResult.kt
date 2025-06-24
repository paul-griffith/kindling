package io.github.paulgriffith.kindling.idb.generic

import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.swing.table.AbstractTableModel

sealed interface QueryResult {
    class Success(
        val columnNames: List<String>,
        private val columnTypes: List<Class<*>>,
        val data: List<List<*>>,
    ) : QueryResult, AbstractTableModel() {
        constructor() : this(emptyList(), emptyList(), emptyList())

        init {
            require(columnNames.size == columnTypes.size)
        }

        private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS")

        override fun getRowCount(): Int = data.size
        override fun getColumnCount(): Int = columnNames.size
        override fun getColumnName(columnIndex: Int): String = columnNames[columnIndex]
        override fun getColumnClass(columnIndex: Int): Class<*> = columnTypes[columnIndex]
        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
            val originalValue = data[rowIndex][columnIndex]
            val columnType = columnTypes[columnIndex]
            return when {
                originalValue is Timestamp -> {
                    dateTimeFormatter.format(
                        originalValue.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                    )
                }
                columnType == Timestamp::class.java && originalValue is Long -> {
                    dateTimeFormatter.format(
                        Instant.ofEpochMilli(originalValue)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                    )
                }
                else -> originalValue
            }
        }
    }

    class Error(
        val details: String,
    ) : QueryResult
}

package io.github.paulgriffith.kindling.cache

data class SchemaRecord(
    val id: Int,
    val name: String,
    val errors: List<String>,
)

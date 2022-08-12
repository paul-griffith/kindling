package io.github.paulgriffith.kindling.core

data class Detail(
    val title: String,
    val message: String? = null,
    val details: Map<String, String> = emptyMap(),
    val body: List<String> = emptyList()
)

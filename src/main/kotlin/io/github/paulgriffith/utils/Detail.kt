package io.github.paulgriffith.utils

data class Detail(
    val title: String,
    val message: String? = null,
    val details: Map<String, String> = emptyMap(),
    val body: List<String> = emptyList(),
)

package io.github.paulgriffith.cache

data class CacheEntry(
    val id: Int,
    val schemaId: Int,
    val timestamp: String,
    val attemptCount: Int,
    val dataCount: Int,
)

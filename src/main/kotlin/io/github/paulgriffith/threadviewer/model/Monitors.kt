package io.github.paulgriffith.threadviewer.model

import kotlinx.serialization.Serializable

@Serializable
data class Monitors(
    val lock: String,
    val frame: String,
)

package io.github.paulgriffith.kindling.thread.model

import kotlinx.serialization.Serializable

@JvmInline
@Serializable
value class Stacktrace(val stack: List<String> = emptyList()) : List<String> by stack

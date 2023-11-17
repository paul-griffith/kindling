package io.github.inductiveautomation.kindling.idb.tagconfig.model

data class TagProviderRecord(
    val id: Int,
    val name: String,
    val uuid: String,
    val description: String?,
    val enabled: Boolean,
    val typeId: String,
    val allowBackFill: Boolean,
)

package io.github.paulgriffith.kindling.cache.model

import com.inductiveautomation.ignition.gateway.audit.AuditRecord
import java.io.Serializable

@Suppress("unused")
class AuditProfileData(
    val auditRecord: AuditRecord,
    val insertQuery: String,
    val parentLog: String,
) : Serializable {
    companion object {
        @JvmStatic
        private val serialVersionUID = 3037488986978918285L
    }
}

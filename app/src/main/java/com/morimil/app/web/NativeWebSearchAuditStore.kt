package com.morimil.app.web

import android.content.Context
import org.json.JSONObject

object NativeWebSearchAuditStore {
    fun append(context: Context, entry: NativeWebSearchAuditEntry) {
        val payload = JSONObject()
            .put("auditId", entry.auditId)
            .put("queryOriginal", entry.queryOriginal.take(MAX_QUERY_CHARS))
            .put("querySearch", entry.querySearch.take(MAX_QUERY_CHARS))
            .put("intent", entry.intent.take(MAX_FIELD_CHARS))
            .put("strategy", entry.strategy.take(MAX_FIELD_CHARS))
            .put("primaryUrl", entry.primaryUrl?.take(MAX_URL_CHARS))
            .put("primaryHost", entry.primaryHost?.take(MAX_FIELD_CHARS))
            .put("primaryScore", entry.primaryScore)
            .put("primaryReason", entry.primaryReason?.take(MAX_REASON_CHARS))
            .put("secondaryUrl", entry.secondaryUrl?.take(MAX_URL_CHARS))
            .put("secondaryHost", entry.secondaryHost?.take(MAX_FIELD_CHARS))
            .put("secondaryScore", entry.secondaryScore)
            .put("secondaryReason", entry.secondaryReason?.take(MAX_REASON_CHARS))
            .put("verifierStatus", entry.verifierStatus.take(MAX_FIELD_CHARS))
            .put("verifierConfidence", entry.verifierConfidence.take(MAX_FIELD_CHARS))
            .put("verifierReason", entry.verifierReason.take(MAX_REASON_CHARS))
            .put("retryCount", entry.retryCount)
            .put("fallbackCount", entry.fallbackCount)
            .put("navigationEventCount", entry.navigationEventCount)
            .put("result", entry.result.take(MAX_FIELD_CHARS))
            .put("createdAtMillis", entry.createdAtMillis)
            .toString()

        context.applicationContext.openFileOutput(AUDIT_FILE_NAME, Context.MODE_APPEND).use { output ->
            output.write(payload.toByteArray(Charsets.UTF_8))
            output.write('\n'.code)
        }
    }

    private const val AUDIT_FILE_NAME = "morimil_native_web_search_audit.jsonl"
    private const val MAX_QUERY_CHARS = 420
    private const val MAX_URL_CHARS = 600
    private const val MAX_FIELD_CHARS = 120
    private const val MAX_REASON_CHARS = 360
}

data class NativeWebSearchAuditEntry(
    val auditId: String,
    val queryOriginal: String,
    val querySearch: String,
    val intent: String,
    val strategy: String,
    val primaryUrl: String?,
    val primaryHost: String?,
    val primaryScore: Int?,
    val primaryReason: String?,
    val secondaryUrl: String?,
    val secondaryHost: String?,
    val secondaryScore: Int?,
    val secondaryReason: String?,
    val verifierStatus: String,
    val verifierConfidence: String,
    val verifierReason: String,
    val retryCount: Int,
    val fallbackCount: Int,
    val navigationEventCount: Int,
    val result: String,
    val createdAtMillis: Long
)

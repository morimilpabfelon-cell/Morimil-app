package com.morimil.app.agent

import org.json.JSONObject
import java.net.URI

object LanAgentCapabilities {
    const val FILE_AUDIT_READ_ONLY = "file_audit.read_only"
}

data class LanAgentEndpoint(val baseUrl: String) {
    fun normalizedBaseUrl(): String = baseUrl.trim().trimEnd('/')
}

data class LanFileAuditRequest(
    val requestId: String,
    val targetRootId: String,
    val approved: Boolean,
    val includeContents: Boolean = false,
    val maxFiles: Int = 50_000,
    val maxBytesPerFile: Int = 10 * 1024 * 1024,
    val nonce: String,
    val createdAtMillis: Long
) {
    val capability: String = LanAgentCapabilities.FILE_AUDIT_READ_ONLY

    fun toJsonString(): String {
        return JSONObject()
            .put("request_id", requestId)
            .put("capability", capability)
            .put("target_root_id", targetRootId)
            .put("include_contents", includeContents)
            .put("max_files", maxFiles)
            .put("max_bytes_per_file", maxBytesPerFile)
            .put("nonce", nonce)
            .put("timestamp_ms", createdAtMillis)
            .toString()
    }
}

data class LanAgentFileAuditResult(
    val requestId: String,
    val status: String,
    val agent: String,
    val capability: String,
    val targetRootId: String,
    val artifactHash: String,
    val envelopeHash: String,
    val fileCount: Int,
    val totalBytes: Long
) {
    companion object {
        fun fromJson(raw: String): LanAgentFileAuditResult {
            val root = JSONObject(raw)
            val report = root.getJSONObject("report")
            return LanAgentFileAuditResult(
                requestId = root.getString("request_id"),
                status = root.getString("status"),
                agent = root.getString("agent"),
                capability = root.getString("capability"),
                targetRootId = root.getString("target_root_id"),
                artifactHash = root.getString("artifact_hash"),
                envelopeHash = root.getString("envelope_hash"),
                fileCount = report.getInt("file_count"),
                totalBytes = report.getLong("total_bytes")
            )
        }
    }
}

data class LanAgentPolicyDecision(
    val allowed: Boolean,
    val reasons: List<String> = emptyList()
) {
    companion object {
        fun allow(): LanAgentPolicyDecision = LanAgentPolicyDecision(true)
        fun deny(reasons: List<String>): LanAgentPolicyDecision = LanAgentPolicyDecision(false, reasons)
    }
}

class LanAgentPolicy {
    fun validateFileAuditDispatch(
        endpoint: LanAgentEndpoint,
        request: LanFileAuditRequest,
        pairingKey: String
    ): LanAgentPolicyDecision {
        val reasons = mutableListOf<String>()

        if (!request.approved) reasons += "human approval missing"
        if (!isLocalLanEndpoint(endpoint.baseUrl)) reasons += "endpoint is not local LAN"
        if (pairingKey.isBlank()) reasons += "pairing key missing"
        if (request.requestId.isBlank()) reasons += "request_id missing"
        if (request.targetRootId.isBlank()) reasons += "target_root_id missing"
        if (request.nonce.isBlank()) reasons += "nonce missing"
        if (request.includeContents) reasons += "file contents are not allowed in file audit"
        if (request.maxFiles !in 1..100_000) reasons += "max_files out of range"
        if (request.maxBytesPerFile !in 1..(100 * 1024 * 1024)) reasons += "max_bytes_per_file out of range"

        return if (reasons.isEmpty()) LanAgentPolicyDecision.allow() else LanAgentPolicyDecision.deny(reasons)
    }

    companion object {
        fun isLocalLanEndpoint(rawUrl: String): Boolean {
            val uri = runCatching { URI(rawUrl.trim()) }.getOrNull() ?: return false
            val scheme = uri.scheme?.lowercase() ?: return false
            val host = uri.host?.lowercase() ?: return false
            if (scheme != "http" && scheme != "https") return false
            if (host == "localhost" || host == "10.0.2.2") return true
            if (host.endsWith(".local")) return true
            return isPrivateIpv4(host)
        }

        private fun isPrivateIpv4(host: String): Boolean {
            val parts = host.split('.')
            if (parts.size != 4) return false
            val numbers = parts.map { it.toIntOrNull() ?: return false }
            val first = numbers[0]
            val second = numbers[1]
            return first == 10 ||
                (first == 172 && second in 16..31) ||
                (first == 192 && second == 168) ||
                first == 127
        }
    }
}

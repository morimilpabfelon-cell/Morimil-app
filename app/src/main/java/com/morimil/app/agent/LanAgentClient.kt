package com.morimil.app.agent

import com.morimil.app.net.BoundedHttpBodyReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class LanAgentClient(
    private val policy: LanAgentPolicy = LanAgentPolicy(),
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 15_000
) {
    suspend fun runFileAudit(
        endpoint: LanAgentEndpoint,
        request: LanFileAuditRequest,
        pairCode: String
    ): Result<LanAgentFileAuditResult> {
        val decision = policy.validateFileAuditDispatch(endpoint, request, pairCode)
        if (!decision.allowed) {
            return Result.failure(IllegalStateException(decision.reasons.joinToString("; ")))
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(endpoint.normalizedBaseUrl() + "/file-audit").openConnection() as HttpURLConnection)
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = connectTimeoutMillis
                    connection.readTimeout = readTimeoutMillis
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.setRequestProperty("Accept", "application/json")
                    connection.setRequestProperty("X-Morimil-Pairing", pairCode)

                    connection.outputStream.use { output ->
                        output.write(request.toJsonString().toByteArray(Charsets.UTF_8))
                    }

                    val responseCode = connection.responseCode
                    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    val body = BoundedHttpBodyReader.read(
                        stream = stream,
                        declaredLength = connection.contentLengthLong,
                        maxBytes = if (responseCode in 200..299) MAX_RESPONSE_BYTES else MAX_ERROR_BYTES
                    )
                    if (responseCode !in 200..299) {
                        throw IllegalStateException(
                            "LAN agent rejected request: $responseCode ${body.take(MAX_ERROR_MESSAGE_CHARS)}"
                        )
                    }
                    LanAgentFileAuditResult.fromJson(body).also { result ->
                        require(result.requestId == request.requestId) { "LAN agent response request_id mismatch." }
                        require(result.capability == request.capability) { "LAN agent response capability mismatch." }
                        require(result.targetRootId == request.targetRootId) { "LAN agent response target_root_id mismatch." }
                        require(result.fileCount in 0..request.maxFiles) { "LAN agent response file_count out of range." }
                        require(result.totalBytes >= 0L) { "LAN agent response total_bytes out of range." }
                        require(result.artifactHash.isNotBlank()) { "LAN agent response artifact_hash missing." }
                        require(result.envelopeHash.isNotBlank()) { "LAN agent response envelope_hash missing." }
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }
    }

    private companion object {
        const val MAX_RESPONSE_BYTES = 512 * 1024
        const val MAX_ERROR_BYTES = 64 * 1024
        const val MAX_ERROR_MESSAGE_CHARS = 1_000
    }
}

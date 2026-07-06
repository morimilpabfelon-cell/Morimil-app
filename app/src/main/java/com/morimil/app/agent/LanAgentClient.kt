package com.morimil.app.agent

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
                    val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    if (responseCode !in 200..299) {
                        throw IllegalStateException("LAN agent rejected request: $responseCode")
                    }
                    LanAgentFileAuditResult.fromJson(body)
                } finally {
                    connection.disconnect()
                }
            }
        }
    }
}

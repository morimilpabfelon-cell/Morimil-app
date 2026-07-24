package com.morimil.app.ai

import java.net.URI
import java.security.MessageDigest
import java.util.Locale

internal data class ReasoningCredentialScope(
    val canonicalOrigin: String,
    val storageId: String
)

/**
 * Produces a stable credential scope from the exact remote origin.
 * Paths, query parameters and model names never widen a credential to another host.
 */
internal object ReasoningCredentialScopePolicy {
    fun fromRemoteEndpoint(baseUrl: String): ReasoningCredentialScope {
        require(ReasoningEndpointPolicy.isSecureRemoteEndpoint(baseUrl)) {
            "reasoning_credential_scope_requires_secure_remote_endpoint"
        }
        val uri = runCatching { URI(baseUrl.trim()) }
            .getOrElse { error -> throw IllegalArgumentException("reasoning_credential_scope_invalid", error) }
        require(uri.rawUserInfo == null) { "reasoning_credential_scope_userinfo_denied" }
        val host = uri.host?.trim()?.lowercase(Locale.ROOT).orEmpty()
        require(host.isNotBlank()) { "reasoning_credential_scope_host_missing" }
        val port = if (uri.port >= 0) uri.port else DEFAULT_HTTPS_PORT
        require(port in 1..65535) { "reasoning_credential_scope_port_invalid" }
        val canonicalHost = if (host.contains(':')) "[$host]" else host
        val origin = "https://$canonicalHost:$port"
        return ReasoningCredentialScope(
            canonicalOrigin = origin,
            storageId = sha256(origin)
        )
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private const val DEFAULT_HTTPS_PORT = 443
}

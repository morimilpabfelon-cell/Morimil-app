package com.morimil.app.ai

import java.net.URI
import java.util.Locale

/**
 * Defines the transport boundary for temporary reasoning providers.
 *
 * Local trust is intentionally limited to exact loopback/ADB hosts used by the
 * Ollama USB bridge. Private-LAN addresses and hostnames that merely begin with
 * an IP-looking prefix are not trusted and never suppress API authentication.
 */
object ReasoningEndpointPolicy {
    fun isLocalTrustedEndpoint(baseUrl: String): Boolean {
        val uri = parseHttpUri(baseUrl) ?: return false
        val host = normalizeHost(uri.host)
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host == "10.0.2.2"
    }

    fun isSecureRemoteEndpoint(baseUrl: String): Boolean {
        val uri = parseHttpUri(baseUrl) ?: return false
        return uri.scheme.equals("https", ignoreCase = true) && !isLocalTrustedEndpoint(baseUrl)
    }

    fun isAllowedTemporaryReasoningEndpoint(baseUrl: String): Boolean {
        return isLocalTrustedEndpoint(baseUrl) || isSecureRemoteEndpoint(baseUrl)
    }

    private fun parseHttpUri(baseUrl: String): URI? {
        val uri = runCatching { URI(baseUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.rawUserInfo != null) return null
        if (uri.host.isNullOrBlank()) return null
        return uri
    }

    private fun normalizeHost(host: String?): String {
        return host.orEmpty()
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
            .lowercase(Locale.ROOT)
    }
}

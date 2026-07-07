package com.morimil.app.ai

import java.net.URI

/**
 * Defines which model endpoints belong to Morimil's local trust boundary.
 *
 * A local endpoint means the model is owned by the user's device/LAN, so it
 * should not require a remote API key and should be routed as LOCAL_OPERATIVE.
 */
object ReasoningEndpointPolicy {
    fun isLocalTrustedEndpoint(baseUrl: String): Boolean {
        val host = runCatching { URI(baseUrl.trim()).host.orEmpty().lowercase() }
            .getOrDefault("")
        if (host.isBlank()) return false
        return isLoopbackOrAndroidHost(host) || isPrivateLanHost(host)
    }

    private fun isLoopbackOrAndroidHost(host: String): Boolean {
        return host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host == "10.0.2.2"
    }

    private fun isPrivateLanHost(host: String): Boolean {
        if (host.startsWith("192.168.")) return true
        val parts = host.split(".")
        if (parts.size != 4) return false
        val first = parts[0].toIntOrNull() ?: return false
        val second = parts[1].toIntOrNull() ?: return false
        return first == 10 || (first == 172 && second in 16..31)
    }
}

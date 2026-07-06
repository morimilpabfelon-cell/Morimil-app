package com.morimil.app.net

import java.net.URL

object NetSourcePolicy {
    fun validateUrl(rawUrl: String): NetSourceDecision {
        val parsed = runCatching { URL(rawUrl) }.getOrNull()
            ?: return NetSourceDecision(false, "invalid_url")
        if (parsed.protocol != "https") {
            return NetSourceDecision(false, "non_https_source")
        }
        return validateHost(parsed.host)
    }

    fun validateHost(host: String?): NetSourceDecision {
        val clean = host.orEmpty().trim().trim('[', ']').lowercase()
        if (clean.isBlank()) return NetSourceDecision(false, "blank_host")
        if (clean == "localhost" || clean.endsWith(".local")) {
            return NetSourceDecision(false, "local_host_denied")
        }
        if (clean == "0.0.0.0" || clean == "::" || clean == "::1") {
            return NetSourceDecision(false, "loopback_denied")
        }
        if (isPrivateIpv4(clean)) return NetSourceDecision(false, "private_ipv4_denied")
        if (isPrivateIpv6(clean)) return NetSourceDecision(false, "private_ipv6_denied")
        return NetSourceDecision(true)
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val numbers = parts.map { part -> part.toIntOrNull() ?: return false }
        if (numbers.any { value -> value !in 0..255 }) return false
        val first = numbers[0]
        val second = numbers[1]
        return first == 10 ||
            first == 127 ||
            first == 169 && second == 254 ||
            first == 172 && second in 16..31 ||
            first == 192 && second == 168 ||
            first == 100 && second in 64..127
    }

    private fun isPrivateIpv6(host: String): Boolean {
        val clean = host.lowercase()
        return clean == "::1" ||
            clean.startsWith("fc") ||
            clean.startsWith("fd") ||
            clean.startsWith("fe80")
    }
}

data class NetSourceDecision(
    val allowed: Boolean,
    val reason: String = "allowed"
)

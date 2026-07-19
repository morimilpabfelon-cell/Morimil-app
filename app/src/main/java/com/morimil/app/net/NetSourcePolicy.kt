package com.morimil.app.net

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL

object NetSourcePolicy {
    fun validateUrl(rawUrl: String): NetSourceDecision {
        val parsed = runCatching { URL(rawUrl) }.getOrNull()
            ?: return NetSourceDecision(false, "invalid_url")
        if (parsed.protocol != "https") {
            return NetSourceDecision(false, "non_https_source")
        }
        if (parsed.userInfo != null) {
            return NetSourceDecision(false, "userinfo_denied")
        }
        return validateHost(parsed.host)
    }

    fun validateHost(host: String?): NetSourceDecision {
        val clean = normalizeHost(host)
        if (clean.isBlank()) return NetSourceDecision(false, "blank_host")
        if (
            clean == "localhost" ||
            clean.endsWith(".localhost") ||
            clean.endsWith(".local") ||
            clean.endsWith(".internal") ||
            clean.endsWith(".home.arpa")
        ) {
            return NetSourceDecision(false, "local_host_denied")
        }

        parseLiteralAddress(clean)?.let { address ->
            return validateAddress(address)
        }
        if (looksLikeNonCanonicalNumericHost(clean)) {
            return NetSourceDecision(false, "noncanonical_numeric_host_denied")
        }
        return NetSourceDecision(true)
    }

    /** Call only from an IO worker immediately before opening an HTTP connection. */
    fun validateResolvedHost(host: String?): NetSourceDecision {
        val syntaxDecision = validateHost(host)
        if (!syntaxDecision.allowed) return syntaxDecision
        val clean = normalizeHost(host)
        val addresses = runCatching { InetAddress.getAllByName(clean).toList() }
            .getOrElse { return NetSourceDecision(false, "dns_resolution_failed") }
        if (addresses.isEmpty()) return NetSourceDecision(false, "dns_resolution_empty")

        addresses.forEach { address ->
            val decision = validateAddress(address)
            if (!decision.allowed) {
                return NetSourceDecision(false, "dns_${decision.reason}")
            }
        }
        return NetSourceDecision(true)
    }

    private fun parseLiteralAddress(host: String): InetAddress? {
        if (host.contains(':')) {
            return runCatching { InetAddress.getByName(host) }.getOrNull()
        }
        val parts = host.split('.')
        if (parts.size != 4) return null
        val numbers = parts.map { part ->
            if (part.isEmpty() || part.length > 3 || part.any { !it.isDigit() }) return null
            part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        }
        return InetAddress.getByAddress(numbers.map { it.toByte() }.toByteArray())
    }

    private fun validateAddress(address: InetAddress): NetSourceDecision {
        if (address.isAnyLocalAddress) return NetSourceDecision(false, "unspecified_address_denied")
        if (address.isLoopbackAddress) return NetSourceDecision(false, "loopback_denied")
        if (address.isLinkLocalAddress) return NetSourceDecision(false, "link_local_denied")
        if (address.isSiteLocalAddress) return NetSourceDecision(false, "site_local_denied")
        if (address.isMulticastAddress) return NetSourceDecision(false, "multicast_denied")

        return when (address) {
            is Inet4Address -> if (isPublicIpv4(address.address)) {
                NetSourceDecision(true)
            } else {
                NetSourceDecision(false, "non_public_ipv4_denied")
            }
            is Inet6Address -> if (isPublicIpv6(address.address)) {
                NetSourceDecision(true)
            } else {
                NetSourceDecision(false, "non_public_ipv6_denied")
            }
            else -> NetSourceDecision(false, "unknown_address_family_denied")
        }
    }

    private fun isPublicIpv4(bytes: ByteArray): Boolean {
        if (bytes.size != 4) return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        val third = bytes[2].toInt() and 0xff

        return when {
            first == 0 -> false
            first == 10 -> false
            first == 100 && second in 64..127 -> false
            first == 127 -> false
            first == 169 && second == 254 -> false
            first == 172 && second in 16..31 -> false
            first == 192 && second == 0 && third == 0 -> false
            first == 192 && second == 0 && third == 2 -> false
            first == 192 && second == 168 -> false
            first == 198 && second in 18..19 -> false
            first == 198 && second == 51 && third == 100 -> false
            first == 203 && second == 0 && third == 113 -> false
            first >= 224 -> false
            else -> true
        }
    }

    private fun isPublicIpv6(bytes: ByteArray): Boolean {
        if (bytes.size != 16) return false
        val first = bytes[0].toInt() and 0xff
        val second = bytes[1].toInt() and 0xff
        val third = bytes[2].toInt() and 0xff
        val fourth = bytes[3].toInt() and 0xff

        val isGlobalUnicast = first in 0x20..0x3f
        val isDocumentation = first == 0x20 && second == 0x01 && third == 0x0d && fourth == 0xb8
        return isGlobalUnicast && !isDocumentation
    }

    private fun looksLikeNonCanonicalNumericHost(host: String): Boolean {
        if (host.startsWith("0x")) return true
        if (host.firstOrNull()?.isDigit() != true) return false
        return host.all { character ->
            character.isDigit() ||
                character == '.' ||
                character == 'x' ||
                character in 'a'..'f'
        }
    }

    private fun normalizeHost(host: String?): String {
        return host.orEmpty()
            .trim()
            .trim('[', ']')
            .trimEnd('.')
            .lowercase()
    }
}

data class NetSourceDecision(
    val allowed: Boolean,
    val reason: String = "allowed"
)

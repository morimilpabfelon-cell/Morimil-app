package com.morimil.app.core.identity

import java.security.MessageDigest
import java.util.Locale

object StableIdDigest {
    fun shortSha256Hex(
        namespace: String,
        parts: List<String>,
        hexLength: Int = DEFAULT_HEX_LENGTH
    ): String {
        require(hexLength in 8..64) { "hexLength must be between 8 and 64." }
        val canonicalPayload = buildString {
            append("morimil.stable_id.v1\n")
            append("namespace:")
            append(namespace.length)
            append(":")
            append(namespace)
            append("\n")
            parts.forEach { part ->
                append("part:")
                append(part.length)
                append(":")
                append(part)
                append("\n")
            }
        }
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(canonicalPayload.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { byte ->
            "%02x".format(Locale.US, byte.toInt() and 0xff)
        }.take(hexLength)
    }

    private const val DEFAULT_HEX_LENGTH = 20
}

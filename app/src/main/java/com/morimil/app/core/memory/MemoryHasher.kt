package com.morimil.app.core.memory

import java.security.MessageDigest

class MemoryHasher {
    fun hash(fields: Map<String, Any?>): String {
        val canonical = stableStringify(fields)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return "sha256:" + digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun stableStringify(value: Any?): String {
        return when (value) {
            null -> "null"
            is String -> quoteJsonString(value)
            is Number, is Boolean -> value.toString()
            is Map<*, *> -> value.keys
                .filterIsInstance<String>()
                .sorted()
                .joinToString(prefix = "{", postfix = "}", separator = ",") { key ->
                    "${quoteJsonString(key)}:${stableStringify(value[key])}"
                }
            else -> quoteJsonString(value.toString())
        }
    }

    private fun quoteJsonString(value: String): String {
        val output = StringBuilder(value.length + 2)
        output.append('"')
        value.forEach { char ->
            when (char) {
                '"' -> output.append("\\\"")
                '\\' -> output.append("\\\\")
                '\b' -> output.append("\\b")
                '\u000C' -> output.append("\\f")
                '\n' -> output.append("\\n")
                '\r' -> output.append("\\r")
                '\t' -> output.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        output.append("\\u")
                        output.append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        output.append(char)
                    }
                }
            }
        }
        output.append('"')
        return output.toString()
    }
}

package com.morimil.app.net

object NetIntentDetector {
    private val markers = listOf(
        word(98, 117, 115, 99, 97),
        word(105, 110, 118, 101, 115, 116, 105, 103, 97),
        word(114, 101, 118, 105, 115, 97),
        word(97, 99, 116, 117, 97, 108),
        word(104, 111, 121)
    )

    fun shouldUseNet(message: String): Boolean {
        val clean = message.trim()
        if (clean.isEmpty()) return false
        if (NetUrlExtractor.extract(clean).isNotEmpty()) return true
        val lower = clean.lowercase()
        return markers.any { marker -> marker in lower }
    }

    private fun word(vararg values: Int): String {
        return values.map { value -> value.toChar() }.joinToString("")
    }
}

object NetUrlExtractor {
    fun extract(message: String): List<String> {
        val scheme = "h" + "ttp"
        return message.split(Regex("\\s+"))
            .map { token -> token.trim().trimEnd('.', ',', ';', ':', ')', ']') }
            .filter { token -> token.startsWith(scheme) }
            .distinct()
            .take(4)
    }
}

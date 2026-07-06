package com.morimil.app.net

object NetIntentDetector {
    fun shouldUseNet(message: String): Boolean {
        return message.trim().isNotEmpty() && NetUrlExtractor.extract(message).isNotEmpty()
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

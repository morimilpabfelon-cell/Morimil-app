package com.morimil.app.net

object NetTextExtractor {
    fun compact(value: String, maxChars: Int = 8_000): String {
        return value
            .lines()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
            .joinToString("\n")
            .take(maxChars)
    }
}

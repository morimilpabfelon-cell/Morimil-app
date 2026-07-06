package com.morimil.app.net

object NetTextExtractor {
    fun readable(value: String, maxChars: Int = 8_000): String {
        val withoutBlocks = removeBetween(value, "script")
            .let { removeBetween(it, "style") }
            .let { removeBetween(it, "svg") }
            .let { removeBetween(it, "noscript") }
        val tagless = withoutBlocks.replace(Regex("<[^>]+>"), " ")
        return compact(decode(tagless), maxChars)
    }

    fun compact(value: String, maxChars: Int = 8_000): String {
        return value
            .lines()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() }
            .joinToString("\n")
            .replace(Regex("[ \\t]+"), " ")
            .take(maxChars)
    }

    fun decode(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&nbsp;", " ")
    }

    private fun removeBetween(value: String, tag: String): String {
        var output = value
        val pattern = Regex("<" + tag + "[\\s\\S]*?</" + tag + ">", RegexOption.IGNORE_CASE)
        while (pattern.containsMatchIn(output)) {
            output = output.replace(pattern, " ")
        }
        return output
    }
}

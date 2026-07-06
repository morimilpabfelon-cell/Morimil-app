package com.morimil.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

class NetEvidenceProvider(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 20_000
) {
    suspend fun build(message: String): String = withContext(Dispatchers.IO) {
        if (NetIntentDetector.shouldUseNet(message).not()) return@withContext ""
        val directUrls = NetUrlExtractor.extract(message)
        val targets = if (directUrls.isNotEmpty()) {
            directUrls
        } else {
            resolveLookupTargets(message)
        }
        val blocks = targets.take(3).mapNotNull { url -> runCatching { read(url) }.getOrNull() }
        if (blocks.isEmpty()) {
            "FUENTE_EXTERNA=consulta_nativa_sin_resultado\nEXTRACTO=Morimil intento consultar fuentes externas para este turno, pero no obtuvo texto util. No inventes datos actuales."
        } else {
            blocks.joinToString("\n\n")
        }
    }

    private fun resolveLookupTargets(query: String): List<String> {
        val lookup = lookupUrl(query)
        val markup = runCatching { fetchRaw(lookup) }.getOrDefault("")
        val resultLinks = extractResultLinks(markup)
        return if (resultLinks.isEmpty()) listOf(lookup) else resultLinks
    }

    private fun lookupUrl(query: String): String {
        val base = "h" + "ttps://html." + "duck" + "duckgo" + ".com/html/?q="
        return base + URLEncoder.encode(query.take(180), "UTF-8")
    }

    private fun read(rawUrl: String): String {
        val raw = fetchRaw(rawUrl)
        val text = NetTextExtractor.readable(raw, 6_000)
        return "FUENTE_EXTERNA=$rawUrl\nEXTRACTO=\n$text"
    }

    private fun fetchRaw(rawUrl: String): String {
        val url = URL(rawUrl)
        require(url.protocol == "https")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("Accept", "text/html,text/plain")
            setRequestProperty("User-Agent", "MorimilNetReader/1.0")
            instanceFollowRedirects = true
        }
        return try {
            require(connection.responseCode in 200..299)
            connection.inputStream.bufferedReader().use { it.readText().take(250_000) }
        } finally {
            connection.disconnect()
        }
    }

    private fun extractResultLinks(markup: String): List<String> {
        val hrefs = Regex("href=\\\"([^\\\"]+)\\\"").findAll(markup)
            .map { match -> match.groupValues[1] }
            .toList()
        return hrefs.mapNotNull { href -> normalizeLookupHref(href) }
            .filter { value -> value.startsWith("https://") }
            .distinct()
            .take(5)
    }

    private fun normalizeLookupHref(href: String): String? {
        val cleaned = NetTextExtractor.readable(href, 2_000)
        val marker = "uddg="
        val index = cleaned.indexOf(marker)
        if (index >= 0) {
            val encoded = cleaned.substring(index + marker.length).substringBefore('&')
            return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
        }
        return when {
            cleaned.startsWith("//") -> "https:$cleaned"
            cleaned.startsWith("https://") -> cleaned
            else -> null
        }
    }
}

package com.morimil.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class NetEvidenceProvider(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 20_000
) {
    suspend fun build(message: String): String = withContext(Dispatchers.IO) {
        if (message.trim().isEmpty()) return@withContext ""
        val directUrls = NetUrlExtractor.extract(message)
        val targets = if (directUrls.isNotEmpty()) directUrls else listOf(lookupUrl(message))
        targets.take(3).mapNotNull { url -> runCatching { read(url) }.getOrNull() }
            .joinToString("\n\n")
    }

    private fun lookupUrl(query: String): String {
        val base = "h" + "ttps://html." + "duck" + "duckgo" + ".com/html/?q="
        return base + URLEncoder.encode(query.take(180), "UTF-8")
    }

    private fun read(rawUrl: String): String {
        val url = URL(rawUrl)
        require(url.protocol == "https")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("Accept", "text/html,text/plain")
            instanceFollowRedirects = true
        }
        return try {
            require(connection.responseCode in 200..299)
            val raw = connection.inputStream.bufferedReader().use { it.readText().take(250_000) }
            "FUENTE_EXTERNA=$rawUrl\nEXTRACTO=\n${NetTextExtractor.compact(raw, 6_000)}"
        } finally {
            connection.disconnect()
        }
    }
}

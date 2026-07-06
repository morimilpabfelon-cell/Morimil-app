package com.morimil.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class NetEvidenceProvider(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 20_000
) {
    suspend fun build(message: String): String = withContext(Dispatchers.IO) {
        if (!NetIntentDetector.shouldUseNet(message)) return@withContext ""
        val urls = NetUrlExtractor.extract(message)
        if (urls.isEmpty()) return@withContext ""
        urls.take(3).mapNotNull { url -> runCatching { read(url) }.getOrNull() }
            .joinToString("\n\n")
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

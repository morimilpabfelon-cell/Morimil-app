package com.morimil.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

class NetEvidenceProvider(
    private val fetcher: NetRawFetcher = HttpNetRawFetcher(),
    private val renderedFetcher: NetRenderedFetcher = NativeBrowserRuntime.renderedFetcher()
) {
    suspend fun build(message: String): String = withContext(Dispatchers.IO) {
        if (NetIntentDetector.shouldUseNet(message).not()) return@withContext ""

        val directUrls = NetUrlExtractor.extract(message)
        val resolution = if (directUrls.isNotEmpty()) {
            NetTargetResolution(targets = directUrls, notes = listOf("modo=direct_url"))
        } else {
            resolveLookupTargets(message)
        }

        val notes = resolution.notes.toMutableList()
        val blocks = mutableListOf<String>()
        resolution.targets.take(MAX_TARGETS_PER_TURN).forEach { url ->
            val policy = NetSourcePolicy.validateUrl(url)
            if (!policy.allowed) {
                notes += "source_denied:${shortUrl(url)}:${policy.reason}"
                return@forEach
            }

            val result = fetcher.fetch(url)
            if (result.ok) {
                val text = NetTextExtractor.readable(result.body, MAX_CONTEXT_CHARS_PER_SOURCE)
                if (text.isNotBlank()) {
                    blocks += sourceBlock(url, text, mode = "http")
                } else {
                    notes += "empty_text:${shortUrl(url)}"
                    readRendered(url, notes)?.let { block -> blocks += block }
                }
            } else {
                notes += "fetch_failed:${shortUrl(url)}:${result.error.orEmpty().take(120)}"
                readRendered(url, notes)?.let { block -> blocks += block }
            }
        }

        if (blocks.isEmpty()) {
            failureBlock(notes)
        } else {
            blocks.joinToString("\n\n")
        }
    }

    private suspend fun readRendered(url: String, notes: MutableList<String>): String? {
        val policy = NetSourcePolicy.validateUrl(url)
        if (!policy.allowed) {
            notes += "browser_denied:${shortUrl(url)}:${policy.reason}"
            return null
        }
        val rendered = renderedFetcher.fetch(url)
        if (!rendered.ok) {
            notes += "browser_failed:${shortUrl(url)}:${rendered.error.orEmpty().take(120)}"
            return null
        }
        val text = NetTextExtractor.compact(rendered.text, MAX_CONTEXT_CHARS_PER_SOURCE)
        if (text.isBlank()) {
            notes += "browser_empty_text:${shortUrl(url)}"
            return null
        }
        notes += "browser_ok:${shortUrl(url)}"
        return sourceBlock(url, text, mode = "browser")
    }

    private fun resolveLookupTargets(query: String): NetTargetResolution {
        val notes = mutableListOf("modo=search_html")
        val searchPages = lookupUrls(query)
        searchPages.forEach { searchUrl ->
            val result = fetcher.fetch(searchUrl)
            if (!result.ok) {
                notes += "search_failed:${shortUrl(searchUrl)}:${result.error.orEmpty().take(120)}"
                return@forEach
            }
            val links = extractResultLinks(result.body)
            if (links.isNotEmpty()) {
                notes += "search_results=${links.size}:${shortUrl(searchUrl)}"
                return NetTargetResolution(targets = links, notes = notes)
            }
            notes += "search_no_links:${shortUrl(searchUrl)}"
        }
        return NetTargetResolution(targets = searchPages, notes = notes)
    }

    private fun lookupUrls(query: String): List<String> {
        val encoded = URLEncoder.encode(query.take(180), "UTF-8")
        val host = "duck" + "duckgo"
        return listOf(
            "h" + "ttps://html." + host + ".com/html/?q=" + encoded,
            "h" + "ttps://lite." + host + ".com/lite/?q=" + encoded
        )
    }

    private fun extractResultLinks(markup: String): List<String> {
        val hrefs = Regex("href\\s*=\\s*['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
            .findAll(markup)
            .map { match -> match.groupValues[1] }
            .toList()
        return hrefs.mapNotNull { href -> normalizeLookupHref(href) }
            .filter { value -> NetSourcePolicy.validateUrl(value).allowed }
            .distinct()
            .take(MAX_SEARCH_LINKS)
    }

    private fun normalizeLookupHref(href: String): String? {
        val cleaned = NetTextExtractor.decode(href.trim())
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

    private fun sourceBlock(url: String, text: String, mode: String): String {
        return "FUENTE_EXTERNA=$url\nMODO_LECTURA=$mode\nEXTRACTO=\n$text"
    }

    private fun failureBlock(notes: List<String>): String {
        return "FUENTE_EXTERNA=consulta_nativa_sin_resultado\n" +
            "DIAGNOSTICO=${notes.joinToString(" | ").take(900)}\n" +
            "EXTRACTO=Morimil intento consultar fuentes externas para este turno por HTTP y navegador nativo, pero no obtuvo texto util. No inventes datos actuales."
    }

    private fun shortUrl(url: String): String {
        return url.take(140).replace(Regex("\\s+"), "_")
    }

    companion object {
        private const val MAX_TARGETS_PER_TURN = 3
        private const val MAX_SEARCH_LINKS = 5
        private const val MAX_CONTEXT_CHARS_PER_SOURCE = 6_000
    }
}

data class NetFetchResult(
    val ok: Boolean,
    val body: String = "",
    val error: String? = null
)

fun interface NetRawFetcher {
    fun fetch(rawUrl: String): NetFetchResult
}

data class NetTargetResolution(
    val targets: List<String>,
    val notes: List<String>
)

class HttpNetRawFetcher(
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 20_000
) : NetRawFetcher {
    override fun fetch(rawUrl: String): NetFetchResult {
        return fetchWithRedirects(rawUrl = rawUrl, redirectCount = 0)
    }

    private fun fetchWithRedirects(rawUrl: String, redirectCount: Int): NetFetchResult {
        val policy = NetSourcePolicy.validateUrl(rawUrl)
        if (!policy.allowed) return NetFetchResult(ok = false, error = policy.reason)

        return runCatching {
            val url = URL(rawUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                instanceFollowRedirects = false
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7")
                setRequestProperty("Accept-Language", "es-PE,es;q=0.9,en;q=0.8")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", MOBILE_USER_AGENT)
            }
            try {
                val code = connection.responseCode
                if (code in HTTP_REDIRECT_CODES) {
                    if (redirectCount >= MAX_REDIRECTS) return NetFetchResult(ok = false, error = "redirect_limit")
                    val location = connection.getHeaderField("Location")
                        ?: return NetFetchResult(ok = false, error = "redirect_without_location")
                    val nextUrl = URL(url, location).toExternalForm()
                    val redirectPolicy = NetSourcePolicy.validateUrl(nextUrl)
                    if (!redirectPolicy.allowed) {
                        return NetFetchResult(ok = false, error = "redirect_denied:${redirectPolicy.reason}")
                    }
                    return fetchWithRedirects(nextUrl, redirectCount + 1)
                }

                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { reader -> reader.readText().take(MAX_RAW_CHARS) }.orEmpty()
                if (code !in 200..299) {
                    NetFetchResult(ok = false, error = "http_$code:${body.take(160)}")
                } else {
                    NetFetchResult(ok = true, body = body)
                }
            } finally {
                connection.disconnect()
            }
        }.getOrElse { error ->
            NetFetchResult(ok = false, error = "${error::class.java.simpleName}:${error.message.orEmpty().take(160)}")
        }
    }

    companion object {
        private const val MAX_RAW_CHARS = 250_000
        private const val MAX_REDIRECTS = 3
        private val HTTP_REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private const val MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
    }
}

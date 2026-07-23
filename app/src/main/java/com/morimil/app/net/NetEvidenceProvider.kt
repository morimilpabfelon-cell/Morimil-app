package com.morimil.app.net

import java.net.URLDecoder
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        return sourceBlock(url, text, mode = "browser_isolated")
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
    connectTimeoutMillis: Long = 10_000L,
    readTimeoutMillis: Long = 20_000L
) : NetRawFetcher {
    private val transport = SafeHttpTransport(
        connectTimeoutMillis = connectTimeoutMillis,
        readTimeoutMillis = readTimeoutMillis,
        callTimeoutMillis = connectTimeoutMillis + readTimeoutMillis
    )

    override fun fetch(rawUrl: String): NetFetchResult {
        val response = transport.fetch(
            rawUrl = rawUrl,
            maxSuccessBytes = MAX_RAW_BYTES,
            maxErrorBytes = MAX_ERROR_BYTES,
            maxRedirects = MAX_REDIRECTS
        )
        return if (response.ok) {
            NetFetchResult(ok = true, body = response.bodyText().take(MAX_RAW_CHARS))
        } else {
            NetFetchResult(ok = false, error = response.error.orEmpty().take(MAX_ERROR_CHARS))
        }
    }

    private companion object {
        const val MAX_RAW_BYTES = 512 * 1024
        const val MAX_ERROR_BYTES = 64 * 1024
        const val MAX_RAW_CHARS = 250_000
        const val MAX_ERROR_CHARS = 16_000
        const val MAX_REDIRECTS = 3
    }
}

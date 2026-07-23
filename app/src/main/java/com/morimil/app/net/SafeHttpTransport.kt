package com.morimil.app.net

import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.util.Locale
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun interface NetAddressResolver {
    @Throws(UnknownHostException::class)
    fun lookup(hostname: String): List<InetAddress>
}

internal object SystemNetAddressResolver : NetAddressResolver {
    override fun lookup(hostname: String): List<InetAddress> = Dns.SYSTEM.lookup(hostname)
}

/**
 * Returns only public addresses to OkHttp. The exact validated list is the list
 * OkHttp connects to, eliminating the validate-then-resolve DNS rebinding gap.
 */
internal class PublicOnlyDns(
    private val resolver: NetAddressResolver = SystemNetAddressResolver
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val syntax = NetSourcePolicy.validateHost(hostname)
        if (!syntax.allowed) throw denied(hostname, syntax.reason)

        val addresses = try {
            resolver.lookup(hostname)
        } catch (error: UnknownHostException) {
            throw error
        } catch (error: Throwable) {
            throw UnknownHostException("dns_resolution_failed:$hostname").apply { initCause(error) }
        }
        val decision = NetSourcePolicy.validateResolvedAddresses(addresses)
        if (!decision.allowed) throw denied(hostname, decision.reason)
        return addresses.toList()
    }

    private fun denied(hostname: String, reason: String): UnknownHostException {
        return UnknownHostException("source_denied:$hostname:$reason")
    }
}

internal data class SafeHttpResponse(
    val ok: Boolean,
    val finalUrl: String = "",
    val statusCode: Int = 0,
    val reasonPhrase: String = "",
    val mediaType: String = "text/plain",
    val encoding: String = Charsets.UTF_8.name(),
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray = ByteArray(0),
    val error: String? = null
) {
    fun bodyText(): String {
        val charset = runCatching { Charset.forName(encoding) }.getOrDefault(Charsets.UTF_8)
        return body.toString(charset)
    }
}

internal class SafeHttpTransport(
    resolver: NetAddressResolver = SystemNetAddressResolver,
    connectTimeoutMillis: Long = 10_000L,
    readTimeoutMillis: Long = 20_000L,
    callTimeoutMillis: Long = 30_000L
) {
    private val client = OkHttpClient.Builder()
        .dns(PublicOnlyDns(resolver))
        .proxy(Proxy.NO_PROXY)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
        .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
        .callTimeout(callTimeoutMillis, TimeUnit.MILLISECONDS)
        .build()

    fun fetch(
        rawUrl: String,
        requestHeaders: Map<String, String> = emptyMap(),
        maxSuccessBytes: Int = DEFAULT_MAX_SUCCESS_BYTES,
        maxErrorBytes: Int = DEFAULT_MAX_ERROR_BYTES,
        maxRedirects: Int = DEFAULT_MAX_REDIRECTS
    ): SafeHttpResponse {
        require(maxSuccessBytes > 0) { "maxSuccessBytes must be positive." }
        require(maxErrorBytes > 0) { "maxErrorBytes must be positive." }
        require(maxRedirects >= 0) { "maxRedirects cannot be negative." }

        var currentUrl = rawUrl
        var redirectCount = 0
        while (true) {
            val policy = NetSourcePolicy.validateUrl(currentUrl)
            if (!policy.allowed) {
                return SafeHttpResponse(ok = false, finalUrl = currentUrl, error = policy.reason)
            }

            val request = runCatching {
                Request.Builder()
                    .url(currentUrl)
                    .get()
                    .applySafeHeaders(requestHeaders)
                    .build()
            }.getOrElse { error ->
                return SafeHttpResponse(
                    ok = false,
                    finalUrl = currentUrl,
                    error = "request_build_error:${error::class.java.simpleName}:${error.message.orEmpty().take(160)}"
                )
            }

            val outcome = runCatching {
                client.newCall(request).execute().use { response ->
                    if (response.code in REDIRECT_CODES) {
                        if (redirectCount >= maxRedirects) {
                            return SafeHttpResponse(ok = false, finalUrl = currentUrl, error = "redirect_limit")
                        }
                        val location = response.header("Location")
                            ?: return SafeHttpResponse(
                                ok = false,
                                finalUrl = currentUrl,
                                error = "redirect_without_location"
                            )
                        val nextUrl = response.request.url.resolve(location)?.toString()
                            ?: return SafeHttpResponse(
                                ok = false,
                                finalUrl = currentUrl,
                                error = "redirect_invalid_location"
                            )
                        val redirectPolicy = NetSourcePolicy.validateUrl(nextUrl)
                        if (!redirectPolicy.allowed) {
                            return SafeHttpResponse(
                                ok = false,
                                finalUrl = currentUrl,
                                error = "redirect_denied:${redirectPolicy.reason}"
                            )
                        }
                        currentUrl = nextUrl
                        redirectCount += 1
                        return@use null
                    }

                    val successful = response.code in 200..299
                    val body = response.body
                    val bytes = BoundedHttpBodyReader.readBytes(
                        stream = body.byteStream(),
                        declaredLength = body.contentLength(),
                        maxBytes = if (successful) maxSuccessBytes else maxErrorBytes
                    )
                    val contentType = body.contentType()
                    SafeHttpResponse(
                        ok = successful,
                        finalUrl = response.request.url.toString(),
                        statusCode = response.code,
                        reasonPhrase = response.message.ifBlank { defaultReasonPhrase(response.code) },
                        mediaType = contentType?.let { "${it.type}/${it.subtype}" } ?: "application/octet-stream",
                        encoding = contentType?.charset(Charsets.UTF_8)?.name() ?: Charsets.UTF_8.name(),
                        headers = sanitizeResponseHeaders(response.headers.toMultimap()),
                        body = bytes,
                        error = if (successful) null else "http_${response.code}:${bytes.toString(Charsets.UTF_8).take(160)}"
                    )
                }
            }.getOrElse { error ->
                return SafeHttpResponse(
                    ok = false,
                    finalUrl = currentUrl,
                    error = "${error::class.java.simpleName}:${error.message.orEmpty().take(180)}"
                )
            }
            if (outcome != null) return outcome
        }
    }

    private fun Request.Builder.applySafeHeaders(headers: Map<String, String>): Request.Builder {
        val merged = DEFAULT_REQUEST_HEADERS.toMutableMap()
        headers.forEach { (name, value) ->
            val normalized = name.lowercase(Locale.US)
            if (normalized in ALLOWED_REQUEST_HEADERS && value.length <= MAX_HEADER_VALUE_CHARS) {
                merged[name] = value
            }
        }
        merged.forEach { (name, value) -> header(name, value) }
        return this
    }

    private fun sanitizeResponseHeaders(headers: Map<String, List<String>>): Map<String, String> {
        return buildMap {
            headers.forEach { (name, values) ->
                val normalized = name.lowercase(Locale.US)
                if (normalized !in STRIPPED_RESPONSE_HEADERS && values.isNotEmpty()) {
                    put(name, values.joinToString(", ").take(MAX_HEADER_VALUE_CHARS))
                }
            }
        }
    }

    private fun defaultReasonPhrase(statusCode: Int): String = when (statusCode) {
        in 200..299 -> "OK"
        in 300..399 -> "Redirect"
        in 400..499 -> "Client Error"
        in 500..599 -> "Server Error"
        else -> "HTTP Response"
    }

    private companion object {
        const val DEFAULT_MAX_SUCCESS_BYTES = 512 * 1024
        const val DEFAULT_MAX_ERROR_BYTES = 64 * 1024
        const val DEFAULT_MAX_REDIRECTS = 3
        const val MAX_HEADER_VALUE_CHARS = 2_048
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        val ALLOWED_REQUEST_HEADERS = setOf("accept", "accept-language", "user-agent")
        val STRIPPED_RESPONSE_HEADERS = setOf(
            "connection",
            "content-encoding",
            "content-length",
            "keep-alive",
            "location",
            "proxy-authenticate",
            "proxy-authorization",
            "set-cookie",
            "te",
            "trailer",
            "transfer-encoding",
            "upgrade"
        )
        val DEFAULT_REQUEST_HEADERS = linkedMapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.7",
            "Accept-Language" to "es-PE,es;q=0.9,en;q=0.8",
            "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0 Mobile Safari/537.36"
        )
    }
}

package com.morimil.app.ai

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Minimal HttpURLConnection adapter backed by a hardened OkHttp client.
 * Existing bounded response readers remain the authority for response size.
 */
internal class OkHttpReasoningConnection(
    target: URL,
    private val baseClient: OkHttpClient
) : HttpURLConnection(target) {
    private val requestHeaders = linkedMapOf<String, String>()
    private val requestBuffer = ByteArrayOutputStream()
    private var response: Response? = null
    private var responseStream: InputStream? = null

    init {
        instanceFollowRedirects = false
    }

    override fun connect() {
        executeIfNeeded()
    }

    override fun disconnect() {
        responseStream?.close()
        responseStream = null
        response?.close()
        response = null
        connected = false
    }

    override fun usingProxy(): Boolean = false

    override fun getOutputStream(): OutputStream {
        check(!connected) { "reasoning_request_already_connected" }
        doOutput = true
        return requestBuffer
    }

    override fun setRequestProperty(key: String?, value: String?) {
        require(!key.isNullOrBlank()) { "reasoning_request_header_name_blank" }
        require(value != null) { "reasoning_request_header_value_missing" }
        check(!connected) { "reasoning_request_already_connected" }
        requestHeaders[key] = value
    }

    override fun addRequestProperty(key: String?, value: String?) {
        setRequestProperty(key, value)
    }

    override fun getRequestProperty(key: String?): String? = requestHeaders[key]

    override fun getRequestProperties(): Map<String, List<String>> {
        return requestHeaders.mapValues { (_, value) -> listOf(value) }
    }

    override fun getResponseCode(): Int = executeIfNeeded().code

    override fun getResponseMessage(): String = executeIfNeeded().message

    override fun getInputStream(): InputStream {
        val executed = executeIfNeeded()
        return responseStream ?: executed.body?.byteStream()?.also { responseStream = it }
            ?: ByteArrayInputStream(ByteArray(0))
    }

    override fun getErrorStream(): InputStream? {
        val executed = executeIfNeeded()
        if (executed.code in 200..299) return null
        return responseStream ?: executed.body?.byteStream()?.also { responseStream = it }
    }

    override fun getContentLengthLong(): Long {
        return executeIfNeeded().body?.contentLength() ?: -1L
    }

    override fun getContentLength(): Int {
        val length = contentLengthLong
        return if (length in 0..Int.MAX_VALUE.toLong()) length.toInt() else -1
    }

    override fun getContentType(): String? = executeIfNeeded().header("Content-Type")

    override fun getHeaderField(name: String?): String? {
        val executed = executeIfNeeded()
        return if (name == null) statusLine(executed) else executed.header(name)
    }

    override fun getHeaderFields(): Map<String?, List<String>> {
        val executed = executeIfNeeded()
        return buildMap {
            put(null, listOf(statusLine(executed)))
            executed.headers.names().forEach { name ->
                put(name, executed.headers.values(name))
            }
        }
    }

    private fun executeIfNeeded(): Response {
        response?.let { return it }
        val client = baseClient.newBuilder()
            .connectTimeout(connectTimeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(connectTimeout.toLong(), TimeUnit.MILLISECONDS)
            .build()
        val method = requestMethod.trim().uppercase()
        val mediaType = requestHeaders.entries
            .firstOrNull { (name, _) -> name.equals("Content-Type", ignoreCase = true) }
            ?.value
            ?.toMediaTypeOrNull()
        val body = if (doOutput || method in METHODS_REQUIRING_BODY) {
            requestBuffer.toByteArray().toRequestBody(mediaType)
        } else {
            null
        }
        val builder = Request.Builder()
            .url(url.toString())
            .method(method, body)
        requestHeaders.forEach { (name, value) -> builder.header(name, value) }
        val executed = client.newCall(builder.build()).execute()
        response = executed
        connected = true
        return executed
    }

    private fun statusLine(response: Response): String {
        return "HTTP/1.1 ${response.code} ${response.message}".trimEnd()
    }

    private companion object {
        val METHODS_REQUIRING_BODY = setOf("POST", "PUT", "PATCH")
    }
}

package com.morimil.app.ai

import com.morimil.app.net.NetAddressResolver
import com.morimil.app.net.PublicOnlyDns
import com.morimil.app.net.SystemNetAddressResolver
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.net.URL
import okhttp3.OkHttpClient

/**
 * Network boundary for temporary auxiliary reasoning transports.
 *
 * Remote endpoints use PublicOnlyDns inside the same OkHttp client that opens
 * the socket. This removes the validate-then-resolve DNS rebinding gap.
 */
internal object ReasoningTransportSecurity {
    fun openConnection(
        rawUrl: String,
        resolver: NetAddressResolver = SystemNetAddressResolver
    ): HttpURLConnection {
        val target = validateTarget(rawUrl)
        return OkHttpReasoningConnection(
            target = target,
            baseClient = clientFor(rawUrl, resolver)
        )
    }

    internal fun clientFor(
        rawUrl: String,
        resolver: NetAddressResolver = SystemNetAddressResolver
    ): OkHttpClient {
        validateTarget(rawUrl)
        return OkHttpClient.Builder()
            .apply {
                if (!ReasoningEndpointPolicy.isLocalTrustedEndpoint(rawUrl)) {
                    dns(PublicOnlyDns(resolver))
                }
            }
            .proxy(Proxy.NO_PROXY)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }

    internal fun validateTarget(rawUrl: String): URL {
        require(ReasoningEndpointPolicy.isAllowedTemporaryReasoningEndpoint(rawUrl)) {
            "reasoning_endpoint_not_allowed"
        }
        val uri = runCatching { URI(rawUrl.trim()) }
            .getOrElse { error -> throw IllegalArgumentException("reasoning_endpoint_invalid", error) }
        require(uri.rawUserInfo == null) { "reasoning_endpoint_userinfo_denied" }
        require(uri.rawFragment == null) { "reasoning_endpoint_fragment_denied" }
        require(!uri.host.isNullOrBlank()) { "reasoning_endpoint_host_missing" }
        return runCatching { uri.toURL() }
            .getOrElse { error -> throw IllegalArgumentException("reasoning_endpoint_invalid", error) }
    }

    fun requireNoRedirect(statusCode: Int) {
        require(statusCode !in 300..399) {
            "reasoning_redirect_denied"
        }
    }
}

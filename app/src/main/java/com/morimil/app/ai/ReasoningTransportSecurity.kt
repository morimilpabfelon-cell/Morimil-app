package com.morimil.app.ai

import com.morimil.app.net.NetSourceDecision
import com.morimil.app.net.NetSourcePolicy
import java.net.HttpURLConnection
import java.net.URL

/**
 * Network boundary for temporary auxiliary reasoning transports.
 *
 * A provider may answer one bounded request. It never owns Morimil's identity,
 * memory, continuity, goals, lifecycle or intrinsic motor state.
 */
internal object ReasoningTransportSecurity {
    fun openConnection(
        rawUrl: String,
        resolveHost: (String?) -> NetSourceDecision = NetSourcePolicy::validateResolvedHost
    ): HttpURLConnection {
        val target = validateTarget(rawUrl, resolveHost)
        return (target.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
        }
    }

    internal fun validateTarget(
        rawUrl: String,
        resolveHost: (String?) -> NetSourceDecision
    ): URL {
        require(ReasoningEndpointPolicy.isAllowedTemporaryReasoningEndpoint(rawUrl)) {
            "reasoning_endpoint_not_allowed"
        }
        val target = runCatching { URL(rawUrl) }
            .getOrElse { error -> throw IllegalArgumentException("reasoning_endpoint_invalid", error) }

        if (!ReasoningEndpointPolicy.isLocalTrustedEndpoint(rawUrl)) {
            val decision = resolveHost(target.host)
            require(decision.allowed) {
                "reasoning_endpoint_${decision.reason}"
            }
        }
        return target
    }

    fun requireNoRedirect(statusCode: Int) {
        require(statusCode !in 300..399) {
            "reasoning_redirect_denied"
        }
    }
}
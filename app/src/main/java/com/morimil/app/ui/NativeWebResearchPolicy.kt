package com.morimil.app.ui

internal object NativeWebResearchPolicy {
    fun secondaryCandidateAfter(
        primary: NativeSelectedWebSource?,
        candidates: List<NativeSelectedWebSource>
    ): NativeSelectedWebSource? {
        val primaryUrl = primary?.url.orEmpty()
        val primaryHost = primary?.host.orEmpty()
        return candidates.firstOrNull { candidate ->
            candidate.url != primaryUrl && candidate.host != primaryHost
        }
    }

    fun multiSourceDecision(
        primary: NativeWebCapture,
        secondaryCandidate: NativeSelectedWebSource?
    ): NativeMultiSourceDecision {
        val shouldOpen = primary.confidence != "HIGH" && secondaryCandidate != null
        return NativeMultiSourceDecision(
            shouldOpenSecondary = shouldOpen,
            status = if (shouldOpen) "secondary_required" else "single_source_sufficient",
            confidence = primary.confidence,
            reason = when {
                primary.confidence == "HIGH" -> "fuente primaria con confianza alta"
                secondaryCandidate == null -> "sin segunda fuente candidata"
                else -> "confianza primaria no alta; se requiere contraste"
            }
        )
    }

    fun verifySources(
        primary: NativeWebCapture?,
        secondary: NativeWebCapture?
    ): NativeMultiSourceVerification {
        if (primary == null && secondary == null) {
            return NativeMultiSourceVerification(
                status = "no_sources_captured",
                confidence = "LOW",
                reason = "ninguna fuente capturada con contenido suficiente"
            )
        }
        if (primary == null || secondary == null) {
            val remaining = primary ?: secondary
            return NativeMultiSourceVerification(
                status = "single_source_after_secondary_attempt",
                confidence = remaining?.confidence ?: "LOW",
                reason = "solo una fuente quedo disponible despues del intento de contraste"
            )
        }

        val overlap = NativeWebEvidenceRules.keywordOverlap(primary.text, secondary.text)
        val bothTrusted = NativeWebEvidenceRules.isTrustedTechnicalHost(hostFromDisplayUrl(primary.url)) &&
            NativeWebEvidenceRules.isTrustedTechnicalHost(hostFromDisplayUrl(secondary.url))
        val confidence = when {
            overlap >= 6 && (primary.confidence == "HIGH" || secondary.confidence == "HIGH") -> "HIGH"
            overlap >= 4 && bothTrusted -> "HIGH"
            overlap >= 3 -> "MEDIUM"
            else -> "LOW"
        }
        return NativeMultiSourceVerification(
            status = if (overlap >= 3) "cross_source_supported" else "cross_source_weak_overlap",
            confidence = confidence,
            reason = "overlap_keywords=$overlap primary=${primary.confidence} secondary=${secondary.confidence}"
        )
    }

    fun toVerification(decision: NativeMultiSourceDecision): NativeMultiSourceVerification {
        return NativeMultiSourceVerification(
            status = decision.status,
            confidence = decision.confidence,
            reason = decision.reason
        )
    }

    private fun hostFromDisplayUrl(url: String): String {
        return displayUrl(url).substringBefore('/').removePrefix("www.")
    }

    private fun displayUrl(url: String): String {
        return url
            .removePrefix("https://")
            .removePrefix("http://")
            .removeSuffix("/")
    }
}

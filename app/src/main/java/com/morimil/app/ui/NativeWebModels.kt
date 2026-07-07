package com.morimil.app.ui

internal data class NativeSelectedWebSource(
    val url: String,
    val title: String,
    val host: String,
    val score: Int,
    val reason: String
)

internal data class NativeWebCapture(
    val title: String,
    val url: String,
    val text: String,
    val textChars: Int,
    val source: NativeSelectedWebSource?,
    val confidence: String,
    val evidenceGate: String
)

internal data class NativeMultiSourceDecision(
    val shouldOpenSecondary: Boolean,
    val status: String,
    val confidence: String,
    val reason: String
)

internal data class NativeMultiSourceVerification(
    val status: String,
    val confidence: String,
    val reason: String
)

internal data class NativeNavigationEvent(
    val type: String,
    val detail: String,
    val url: String? = null,
    val title: String? = null,
    val host: String? = null,
    val score: Int? = null,
    val reason: String? = null,
    val timestampMillis: Long
)

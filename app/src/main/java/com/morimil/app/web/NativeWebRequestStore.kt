package com.morimil.app.web

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NativeWebRequestStore {
    private val _pendingRequest = MutableStateFlow<NativeWebRequest?>(null)
    val pendingRequest: StateFlow<NativeWebRequest?> = _pendingRequest.asStateFlow()

    fun requestSearch(query: String) {
        val clean = query.trim().take(MAX_QUERY_CHARS)
        if (clean.isBlank()) return
        _pendingRequest.value = NativeWebRequest(
            query = clean,
            requestedAtMillis = System.currentTimeMillis()
        )
    }

    fun markHandled(request: NativeWebRequest) {
        if (_pendingRequest.value?.requestedAtMillis == request.requestedAtMillis) {
            _pendingRequest.value = null
        }
    }

    private const val MAX_QUERY_CHARS = 240
}

data class NativeWebRequest(
    val query: String,
    val requestedAtMillis: Long
)

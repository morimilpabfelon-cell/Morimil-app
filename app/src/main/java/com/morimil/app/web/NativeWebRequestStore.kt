package com.morimil.app.web

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object NativeWebRequestStore {
    private val _pendingRequest = MutableStateFlow<NativeWebRequest?>(null)
    val pendingRequest: StateFlow<NativeWebRequest?> = _pendingRequest.asStateFlow()

    fun requestSearch(query: String) {
        val plan = WebSearchPlanner.create(query) ?: return
        _pendingRequest.value = NativeWebRequest(
            query = plan.originalQuery,
            searchQuery = plan.searchQuery,
            intent = plan.intent,
            strategy = plan.strategy,
            requestedAtMillis = System.currentTimeMillis()
        )
    }

    fun markHandled(request: NativeWebRequest) {
        if (_pendingRequest.value?.requestedAtMillis == request.requestedAtMillis) {
            _pendingRequest.value = null
        }
    }
}

data class NativeWebRequest(
    val query: String,
    val searchQuery: String = query,
    val intent: WebSearchIntent = WebSearchIntent.GENERAL,
    val strategy: String = "buscar fuentes claras, comparar resultados y abrir la pagina mas util.",
    val requestedAtMillis: Long
)

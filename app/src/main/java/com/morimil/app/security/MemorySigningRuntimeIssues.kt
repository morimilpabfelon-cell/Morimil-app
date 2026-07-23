package com.morimil.app.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MemorySigningRuntimeIssue(
    val component: String,
    val message: String,
    val failureCount: Int,
    val occurredAtMillis: Long
)

interface MemorySigningIssueReporter {
    fun reportKeystoreSigningFailure(keyAlias: String, error: Throwable)
    fun clearKeystoreSigningFailure(keyAlias: String)
}

object MemorySigningRuntimeIssues : MemorySigningIssueReporter {
    const val KEYSTORE_FAILURE_COMPONENT = "memory_signature.keystore_failure"

    private val _latestIssue = MutableStateFlow<MemorySigningRuntimeIssue?>(null)
    val latestIssue: StateFlow<MemorySigningRuntimeIssue?> = _latestIssue.asStateFlow()

    override fun reportKeystoreSigningFailure(keyAlias: String, error: Throwable) {
        val previous = _latestIssue.value
        val failureMessage = buildFailureMessage(keyAlias, error)
        _latestIssue.value = MemorySigningRuntimeIssue(
            component = KEYSTORE_FAILURE_COMPONENT,
            message = failureMessage,
            failureCount = if (previous?.component == KEYSTORE_FAILURE_COMPONENT) {
                previous.failureCount + 1
            } else {
                1
            },
            occurredAtMillis = System.currentTimeMillis()
        )
    }

    override fun clearKeystoreSigningFailure(keyAlias: String) {
        if (_latestIssue.value?.component == KEYSTORE_FAILURE_COMPONENT) {
            _latestIssue.value = null
        }
    }

    private fun buildFailureMessage(keyAlias: String, error: Throwable): String {
        val errorLabel = error.message?.take(140) ?: error::class.java.simpleName
        return "Keystore signing failed for $keyAlias; memory append was blocked: $errorLabel"
    }
}

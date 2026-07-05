package com.morimil.app.ui

data class MemoryIntegrityAuditUiState(
    val memoryChainVerified: Boolean? = null,
    val capsuleChainVerified: Boolean? = null,
    val checkedAtMillis: Long? = null,
    val errorMessage: String? = null
) {
    val statusLabel: String
        get() = when {
            errorMessage != null -> "error"
            memoryChainVerified == true && capsuleChainVerified == true -> "verified"
            memoryChainVerified == false || capsuleChainVerified == false -> "attention"
            else -> "not_checked"
        }
}

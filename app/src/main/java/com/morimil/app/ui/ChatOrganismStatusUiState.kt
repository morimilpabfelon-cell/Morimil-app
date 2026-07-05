package com.morimil.app.ui

data class ChatOrganismStatusUiState(
    val motorLabel: String = "Motor pendiente",
    val modelLabel: String = "modelo pendiente",
    val memoryIntegrityLabel: String = "memoria sin auditar",
    val memoryNeedsAttention: Boolean = false
)

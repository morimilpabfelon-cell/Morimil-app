package com.morimil.app.ui

data class ChatOrganismStatusUiState(
    val intrinsicLabel: String = "Trimotor intrinseco",
    val helperLabel: String = "auxiliar temporal pendiente",
    val helperModelLabel: String = "sin modelo auxiliar",
    val memoryIntegrityLabel: String = "memoria sin auditar",
    val memoryNeedsAttention: Boolean = false
)

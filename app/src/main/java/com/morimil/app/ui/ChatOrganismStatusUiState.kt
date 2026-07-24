package com.morimil.app.ui

data class ChatOrganismStatusUiState(
    val intrinsicLabel: String = "Trimotor intrinseco",
    val helperLabel: String = "auxiliar temporal pendiente",
    val helperModelLabel: String = "sin modelo auxiliar",
    val memoryIntegrityLabel: String = "memoria sin auditar",
    val memoryNeedsAttention: Boolean = false
) {
    /**
     * Compatibility constructor for the existing ViewModel wiring. The former
     * motor fields are interpreted strictly as optional-helper status; they do
     * not redefine Morimil's intrinsic motors.
     */
    constructor(
        motorLabel: String,
        modelLabel: String,
        memoryIntegrityLabel: String,
        memoryNeedsAttention: Boolean
    ) : this(
        intrinsicLabel = "Trimotor intrinseco",
        helperLabel = motorLabel.replace("Motor", "Auxiliar", ignoreCase = true),
        helperModelLabel = modelLabel,
        memoryIntegrityLabel = memoryIntegrityLabel,
        memoryNeedsAttention = memoryNeedsAttention
    )
}

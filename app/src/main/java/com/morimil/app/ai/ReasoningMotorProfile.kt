package com.morimil.app.ai

enum class ReasoningMotorProfile(
    val slotId: Int,
    val displayName: String,
    val storageSuffix: String
) {
    LOCAL(1, "Motor local", "local"),
    SUPERIOR(2, "Motor superior", "superior")
}

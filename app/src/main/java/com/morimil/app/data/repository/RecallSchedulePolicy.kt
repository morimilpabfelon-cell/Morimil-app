package com.morimil.app.data.repository

object RecallSchedulePolicy {
    const val ONE_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

    private val schedulableKinds = setOf(
        "decision",
        "correction",
        "preference",
        "learning",
        "identity",
        "approval",
        "rejection",
        "error_detected",
        "rest_cycle"
    )

    fun shouldSchedule(
        memoryKind: String,
        importance: Int,
        confidence: Int,
        userConfirmed: Boolean
    ): Boolean {
        if (memoryKind == "chat_noise" || memoryKind == "conversation") return false
        if (memoryKind !in schedulableKinds) return false
        return userConfirmed || importance >= 70 || confidence >= 80
    }

    fun priority(
        importance: Int,
        confidence: Int,
        userConfirmed: Boolean
    ): Int {
        val confirmedBoost = if (userConfirmed) 18 else 0
        return ((importance * 0.65f) + (confidence * 0.25f) + confirmedBoost)
            .toInt()
            .coerceIn(1, 100)
    }

    fun nextIntervalDays(currentIntervalDays: Int): Int {
        return when {
            currentIntervalDays <= 0 -> 1
            currentIntervalDays == 1 -> 2
            currentIntervalDays == 2 -> 4
            currentIntervalDays < 14 -> currentIntervalDays + 4
            else -> 30
        }.coerceAtMost(30)
    }

    fun delayMillis(intervalDays: Int): Long {
        return intervalDays.coerceAtLeast(1) * ONE_DAY_MILLIS
    }
}
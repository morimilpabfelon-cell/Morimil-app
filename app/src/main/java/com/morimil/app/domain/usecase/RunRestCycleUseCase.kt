package com.morimil.app.domain.usecase

import com.morimil.app.data.repository.RestCycleRepository

class RunRestCycleUseCase(
    private val restCycleRepository: RestCycleRepository
) {
    suspend operator fun invoke(force: Boolean = false): Boolean {
        return restCycleRepository.runLocalRestCycleIfDue(force = force)
    }

    suspend fun approveAndRun(migrationId: String): Boolean {
        return restCycleRepository.approvePlannedRestCycle(migrationId)
    }
}

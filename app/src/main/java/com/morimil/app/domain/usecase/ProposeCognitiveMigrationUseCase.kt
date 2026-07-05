package com.morimil.app.domain.usecase

import com.morimil.app.data.repository.CognitiveMigrationRepository

class ProposeCognitiveMigrationUseCase(
    private val cognitiveMigrationRepository: CognitiveMigrationRepository
) {
    suspend operator fun invoke(): String? {
        return cognitiveMigrationRepository.proposeCognitiveMigration()
    }

    suspend fun approve(migrationId: String): Boolean {
        return cognitiveMigrationRepository.approveCognitiveMigration(migrationId)
    }

    suspend fun execute(migrationId: String): Boolean {
        return cognitiveMigrationRepository.executeCognitiveMigration(migrationId)
    }

    suspend fun rollback(migrationId: String): Boolean {
        return cognitiveMigrationRepository.rollbackCognitiveMigration(migrationId)
    }
}

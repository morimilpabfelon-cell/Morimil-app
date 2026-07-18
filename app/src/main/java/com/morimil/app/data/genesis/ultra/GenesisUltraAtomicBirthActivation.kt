package com.morimil.app.data.genesis.ultra

import androidx.room.withTransaction
import com.morimil.app.data.local.GenesisUltraBirthCommitEntity
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.MemoryAppendGate

/** Result of one indivisible birth, recovery audit and first canonical append. */
internal class GenesisUltraAtomicBirthActivationResult(
    val commit: GenesisUltraBirthCommitEntity,
    val recoveredBirth: GenesisUltraRecoveredAtomicBirth,
    val firstPostBirthMemory: GenesisUltraCanonicalMemoryAppendResult
)

/**
 * Isolated activation boundary. It is deliberately not connected to onboarding.
 * The database can never expose a committed birth without also containing a
 * verified, Body-signed sequence-1 memory event from that same birth.
 */
internal class GenesisUltraAtomicBirthActivationCoordinator(
    private val database: MorimilDatabase,
    private val checkpoint: suspend (GenesisUltraBirthPersistenceCheckpoint) -> Unit = {},
    private val recoverCommitted: suspend (
        GenesisUltraAtomicBirthStore,
        GenesisUltraAtomicBirthRecoveryRequest
    ) -> GenesisUltraRecoveredAtomicBirth? = { store, request ->
        store.recoverCommittedInsideTransaction(request)
    }
) {
    suspend fun activate(
        verifiedBirth: GenesisUltraVerifiedAtomicBirth,
        persistedAtMillis: Long,
        recoveryRequest: GenesisUltraAtomicBirthRecoveryRequest,
        signer: GenesisUltraBodyMemorySigner,
        firstPostBirthRequest: GenesisUltraCanonicalMemoryAppendRequest
    ): GenesisUltraAtomicBirthActivationResult {
        require(recoveryRequest.bodyRawPublicKey.contentEquals(signer.key.copyRawPublicKey())) {
            "activation_recovery_body_key_mismatch"
        }

        return MemoryAppendGate.withAppendLock {
            database.withTransaction {
                val birthStore = GenesisUltraAtomicBirthStore(database, checkpoint)
                val commit = birthStore.writeVerifiedInsideTransaction(
                    verifiedBirth = verifiedBirth,
                    persistedAtMillis = persistedAtMillis
                )
                val recovered = requireNotNull(recoverCommitted(birthStore, recoveryRequest)) {
                    "activation_recovery_missing_committed_birth"
                }
                val memory = GenesisUltraCanonicalMemoryStore(database).appendInsideTransaction(
                    recoveredBirth = recovered,
                    signer = signer,
                    request = firstPostBirthRequest
                )
                require(memory.event.sequence == 1L) {
                    "activation_first_post_birth_sequence_invalid"
                }
                GenesisUltraAtomicBirthActivationResult(commit, recovered, memory)
            }
        }
    }
}

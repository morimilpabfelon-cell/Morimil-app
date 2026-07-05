package com.morimil.app.security

import android.content.Context
import com.morimil.app.core.memory.MemorySignatureEpochPolicy
import com.morimil.app.core.memory.MemorySignatureEpochRecorder

class SharedPreferencesMemorySignatureEpochPolicy(
    context: Context
) : MemorySignatureEpochPolicy, MemorySignatureEpochRecorder {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun signedEpochEventHash(): String? {
        return preferences.getString(KEY_SIGNED_EPOCH_EVENT_HASH, null)
    }

    override fun recordSignedEvent(eventHash: String) {
        if (preferences.contains(KEY_SIGNED_EPOCH_EVENT_HASH)) return
        preferences.edit()
            .putString(KEY_SIGNED_EPOCH_EVENT_HASH, eventHash)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "morimil_memory_signature_epoch"
        private const val KEY_SIGNED_EPOCH_EVENT_HASH = "signed_epoch_event_hash"
    }
}

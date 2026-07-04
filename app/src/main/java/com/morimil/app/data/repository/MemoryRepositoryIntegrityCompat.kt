package com.morimil.app.data.repository

import com.morimil.app.core.memory.MemoryEventIntegrity

// Bridges repository-local legacy checks to the shared integrity constant.
internal val LEGACY_EVENT_HASH: String
    get() = MemoryEventIntegrity.LEGACY_EVENT_HASH

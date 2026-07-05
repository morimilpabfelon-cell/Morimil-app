package com.morimil.app.runtime

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.morimil.app.data.local.MemoryOrganDatabase
import com.morimil.app.data.local.MorimilDatabase
import com.morimil.app.data.repository.RestCycleRepository
import com.morimil.app.core.memory.MemoryIntegrityCore
import com.morimil.app.security.AndroidKeyStoreMemoryEventSigner
import java.util.concurrent.TimeUnit

class RestCycleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val memoryEventSigner = AndroidKeyStoreMemoryEventSigner()
        val memoryIntegrityCore = MemoryIntegrityCore(signatureVerifier = memoryEventSigner)
        val repository = RestCycleRepository(
            database = MorimilDatabase.getInstance(applicationContext),
            organDatabase = MemoryOrganDatabase.getInstance(applicationContext),
            memoryIntegrityCore = memoryIntegrityCore,
            memoryEventSigner = memoryEventSigner
        )

        return runCatching {
            repository.runLocalRestCycleIfDue()
            Result.success()
        }.getOrElse {
            Result.retry()
        }
    }
}

object RestCycleScheduler {
    const val UNIQUE_WORK_NAME = "morimil_rest_cycle_periodic"
    const val WORK_TAG = "morimil_rest_cycle"
    const val REPEAT_INTERVAL_HOURS = 6L
    const val FLEX_INTERVAL_HOURS = 1L
    const val INITIAL_DELAY_MINUTES = 30L
    const val BACKOFF_MINUTES = 30L

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<RestCycleWorker>(
            repeatInterval = REPEAT_INTERVAL_HOURS,
            repeatIntervalTimeUnit = TimeUnit.HOURS,
            flexTimeInterval = FLEX_INTERVAL_HOURS,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setInitialDelay(INITIAL_DELAY_MINUTES, TimeUnit.MINUTES)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .addTag(WORK_TAG)
            .build()

        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

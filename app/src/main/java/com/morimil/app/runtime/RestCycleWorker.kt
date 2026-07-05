package com.morimil.app.runtime

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.morimil.app.MorimilAppContainer
import java.util.concurrent.TimeUnit

class RestCycleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val repository = MorimilAppContainer.from(applicationContext).restCycleRepository

        return runCatching {
            val didConsolidate = repository.runLocalRestCycleIfDue()
            RestCycleNotifier.notifyRestCycleChecked(
                context = applicationContext,
                didConsolidate = didConsolidate
            )
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
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            buildRequest()
        )
    }

    suspend fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            buildRequest()
        ).result.get()
    }

    suspend fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext)
            .cancelUniqueWork(UNIQUE_WORK_NAME)
            .result
            .get()
    }

    suspend fun readStatus(context: Context): RestCycleScheduleStatus {
        val workInfos = WorkManager.getInstance(context.applicationContext)
            .getWorkInfosForUniqueWork(UNIQUE_WORK_NAME)
            .get()
        return RestCycleScheduleStatus.fromWorkStates(workInfos.map { workInfo -> workInfo.state.name })
    }

    private fun buildRequest(): PeriodicWorkRequest {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        return PeriodicWorkRequestBuilder<RestCycleWorker>(
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
    }
}

data class RestCycleScheduleStatus(
    val uniqueWorkName: String = RestCycleScheduler.UNIQUE_WORK_NAME,
    val states: List<String> = emptyList(),
    val repeatIntervalHours: Long = RestCycleScheduler.REPEAT_INTERVAL_HOURS,
    val flexIntervalHours: Long = RestCycleScheduler.FLEX_INTERVAL_HOURS,
    val initialDelayMinutes: Long = RestCycleScheduler.INITIAL_DELAY_MINUTES,
    val requiresBatteryNotLow: Boolean = true,
    val requiresStorageNotLow: Boolean = true,
    val errorMessage: String? = null
) {
    val isScheduled: Boolean
        get() = states.any { state -> state in ACTIVE_STATES }

    val needsAttention: Boolean
        get() = errorMessage != null || !isScheduled || states.any { state -> state in ATTENTION_STATES }

    val stateLabel: String
        get() = when {
            errorMessage != null -> "error"
            states.isEmpty() -> "no_agendado"
            else -> states.joinToString(",")
        }

    companion object {
        private val ACTIVE_STATES = setOf("ENQUEUED", "RUNNING")
        private val ATTENTION_STATES = setOf("BLOCKED", "CANCELLED", "FAILED")

        fun fromWorkStates(states: List<String>): RestCycleScheduleStatus {
            return RestCycleScheduleStatus(
                states = states
                    .map { state -> state.trim().uppercase() }
                    .filter { state -> state.isNotBlank() }
                    .distinct()
            )
        }
    }
}

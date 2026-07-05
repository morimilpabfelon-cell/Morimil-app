package com.morimil.app.runtime

import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestCycleSchedulerInstrumentedTest {
    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @After
    fun tearDown() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManager.getInstance(context).cancelAllWork().result.get()
    }

    @Test
    fun ensureScheduledRegistersUniquePeriodicRestCycleWork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        RestCycleScheduler.ensureScheduled(context)

        val workInfos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(RestCycleScheduler.UNIQUE_WORK_NAME)
            .get()
        assertTrue(workInfos.any { workInfo -> workInfo.state == WorkInfo.State.ENQUEUED })
    }
}

package com.xc.air3upgrader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

class CheckAppRunningWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("CheckAppRunningWorker: doWork called")
        val dataStoreManager = DataStoreManager(applicationContext)
        val isAutomaticUpgradeReminderEnabled = dataStoreManager.getAutomaticUpgradeReminder().firstOrNull() ?: false
        Timber.d("CheckAppRunningWorker: isAutomaticUpgradeReminderEnabled: $isAutomaticUpgradeReminderEnabled")
        if (isAutomaticUpgradeReminderEnabled) {
            Timber.d("CheckAppRunningWorker: App is running")
        } else {
            Timber.d("CheckAppRunningWorker: App is not running")
        }
        return Result.success()
    }
}
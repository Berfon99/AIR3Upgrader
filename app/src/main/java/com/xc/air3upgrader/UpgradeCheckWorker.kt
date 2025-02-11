package com.xc.air3upgrader

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber
import java.util.Calendar

class UpgradeCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val dataStoreManager = DataStoreManager(appContext)

    override suspend fun doWork(): Result {
        Timber.d("UpgradeCheckWorker: doWork called")

        val lastCheckTime = dataStoreManager.getLastCheckTime().firstOrNull() ?: 0L
        val upgradeCheckInterval = dataStoreManager.getUpgradeCheckInterval().firstOrNull() ?: Interval(0,0,0)
        val currentTime = Calendar.getInstance().timeInMillis
        val isAutomaticUpgradeReminderEnabled = dataStoreManager.getAutomaticUpgradeReminder().firstOrNull() ?: false

        // Set IS_MANUAL_LAUNCH to false for automatic launch
        dataStoreManager.saveIsManualLaunch(false)

        // Convert Interval to milliseconds
        val intervalMillis = (upgradeCheckInterval.days * 24 * 60 * 60 * 1000L) +
                (upgradeCheckInterval.hours * 60 * 60 * 1000L) +
                (upgradeCheckInterval.minutes * 60 * 1000L)

        if (isAutomaticUpgradeReminderEnabled) {
            if (currentTime - lastCheckTime >= intervalMillis) {
                Timber.d("UpgradeCheckWorker: Countdown is zero, setting UNHIDDEN_LAUNCH_ON_REBOOT to true")
                dataStoreManager.saveUnhiddenLaunchOnReboot(true)
            } else {
                Timber.d("UpgradeCheckWorker: Countdown is not zero")
            }
        } else {
            Timber.d("UpgradeCheckWorker: Automatic Upgrade Reminder is disabled")
        }

        return Result.success()
    }
}
package com.xc.air3upgrader

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.Calendar

class UpgradeCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        Timber.d("UpgradeCheckWorker: doWork called")

        val dataStoreManager = DataStoreManager(applicationContext)
        val lastCheckTime = runBlocking { dataStoreManager.getLastCheckTime().first() ?: 0L }
        val upgradeCheckInterval = runBlocking { dataStoreManager.getUpgradeCheckInterval().first() }
        val currentTime = Calendar.getInstance().timeInMillis
        val isAutomaticUpgradeReminderEnabled =
            runBlocking { dataStoreManager.getAutomaticUpgradeReminder().first() }

        // Set IS_MANUAL_LAUNCH to false for automatic launch
        runBlocking {
            dataStoreManager.saveIsManualLaunch(false)
        }
        // Convert Interval to milliseconds
        val intervalMillis = (upgradeCheckInterval.days * 24 * 60 * 60 * 1000L) +
                (upgradeCheckInterval.hours * 60 * 60 * 1000L) +
                (upgradeCheckInterval.minutes * 60 * 1000L)
        if (isAutomaticUpgradeReminderEnabled) {
            if (currentTime - lastCheckTime >= intervalMillis) {
                Timber.d("UpgradeCheckWorker: Countdown is zero, setting UNHIDDEN_LAUNCH_ON_REBOOT to true")
                runBlocking {
                    dataStoreManager.saveUnhiddenLaunchOnReboot(true)
                }
            } else {
                Timber.d("UpgradeCheckWorker: Countdown is not zero")
            }
        } else {
            Timber.d("UpgradeCheckWorker: Automatic Upgrade Reminder is disabled")
        }

        return Result.success()
    }
}
package com.xc.air3upgrader

import android.content.Context
import android.content.Intent
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
        val shouldLaunchOnReboot = runBlocking { dataStoreManager.getUnhiddenLaunchOnReboot().first() }
        val isAutomaticUpgradeReminderEnabled = runBlocking { dataStoreManager.getAutomaticUpgradeReminder().first() }

        // Convert Interval to milliseconds
        val intervalMillis = (upgradeCheckInterval.days * 24 * 60 * 60 * 1000L) +
                (upgradeCheckInterval.hours * 60 * 60 * 1000L) +
                (upgradeCheckInterval.minutes * 60 * 1000L)
        Timber.d("UpgradeCheckWorker: shouldLaunchOnReboot flag value: $shouldLaunchOnReboot")
        Timber.d("UpgradeCheckWorker: isAutomaticUpgradeReminderEnabled flag value: $isAutomaticUpgradeReminderEnabled")

        if (currentTime - lastCheckTime >= intervalMillis) {
            Timber.d("UpgradeCheckWorker: Time to check if we should launch the app")
            if (isAutomaticUpgradeReminderEnabled) {
                if (shouldLaunchOnReboot) {
                    Timber.d("UpgradeCheckWorker: Time to launch the app")
                    launchMainActivity()
                    // Set the flag to false after launching
                    runBlocking {
                        dataStoreManager.saveUnhiddenLaunchOnReboot(false)
                    }
                } else {
                    Timber.d("UpgradeCheckWorker: Not time to launch the app")
                }
            } else {
                Timber.d("UpgradeCheckWorker: Automatic Upgrade Reminder is disabled")
            }
            // Use runBlocking to call the suspend function
            runBlocking {
                dataStoreManager.saveLastCheckTime(currentTime)
            }
        } else {
            Timber.d("UpgradeCheckWorker: Not time to launch the app")
        }

        return Result.success()
    }
    private fun launchMainActivity() {
        Timber.d("UpgradeCheckWorker: launchMainActivity called")
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        applicationContext.startActivity(intent)
    }
}
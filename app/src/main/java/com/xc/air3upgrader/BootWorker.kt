package com.xc.air3upgrader

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import java.util.Calendar

class BootWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        Log.d("BootWorker", "BootWorker: doWork called")
        val dataStoreManager = DataStoreManager(applicationContext)
        val unhiddenLaunchOnReboot: Boolean = runBlocking {
            dataStoreManager.getUnhiddenLaunchOnReboot().firstOrNull() ?: false
        }
        val isAutomaticUpgradeReminderEnabled: Boolean = runBlocking {
            dataStoreManager.getAutomaticUpgradeReminder().firstOrNull() ?: false
        }
        Log.d("BootWorker", "BootWorker: unhiddenLaunchOnReboot: $unhiddenLaunchOnReboot")
        Log.d("BootWorker", "BootWorker: isAutomaticUpgradeReminderEnabled: $isAutomaticUpgradeReminderEnabled")

        // Only launch MainActivity if unhiddenLaunchOnReboot is true
        if (unhiddenLaunchOnReboot) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("unhiddenLaunchOnReboot", unhiddenLaunchOnReboot)
            }
            applicationContext.startActivity(intent)
        } else {
            // Check if the interval has elapsed and set unhiddenLaunchOnReboot if needed
            if (isAutomaticUpgradeReminderEnabled) {
                checkAndSetUnhiddenLaunchIfNeeded()
            }
        }
        Log.d("BootWorker", "BootWorker: doWork - END")
        return Result.success()
    }

    private fun checkAndSetUnhiddenLaunchIfNeeded() {
        Log.d("BootWorker", "BootWorker: checkAndSetUnhiddenLaunchIfNeeded called")
        val dataStoreManager = DataStoreManager(applicationContext)
        val interval = runBlocking {
            dataStoreManager.getUpgradeCheckInterval().firstOrNull() ?: Interval(0, 0, 0)
        }
        var lastCheckTime = runBlocking {
            dataStoreManager.getLastCheckTime().firstOrNull() ?: 0L
        }
        val currentTime = Calendar.getInstance().timeInMillis

        Log.d("BootWorker", "BootWorker: checkAndSetUnhiddenLaunchIfNeeded - interval: $interval")
        Log.d("BootWorker", "BootWorker: checkAndSetUnhiddenLaunchIfNeeded - lastCheckTime: $lastCheckTime")
        Log.d("BootWorker", "BootWorker: checkAndSetUnhiddenLaunchIfNeeded - currentTime: $currentTime")

        val intervalMillis = (interval.days * 24 * 60 * 60 * 1000L) + (interval.hours * 60 * 60 * 1000L) + (interval.minutes * 60 * 1000L)
        val timeElapsed = currentTime - lastCheckTime
        var timeRemaining = intervalMillis - timeElapsed

        Log.d("BootWorker", "BootWorker: checkAndSetUnhiddenLaunchIfNeeded - intervalMillis: $intervalMillis")
        Log.d("BootWorker", "BootWorker: checkAndSetUnhiddenLaunchIfNeeded - timeElapsed: $timeElapsed")
        Log.d("BootWorker", "BootWorker: checkAndSetUnhiddenLaunchIfNeeded - timeRemaining (before check): $timeRemaining")

        // Check if timeRemaining is equal to 0
        if (timeRemaining <= 0L) {
            Log.d("BootWorker", "BootWorker: checkAndSetUnhiddenLaunchIfNeeded - timeRemaining is equal or less than 0")
            runBlocking {
                dataStoreManager.saveUnhiddenLaunchOnReboot(true)
            }
            Log.d("BootWorker", "BootWorker: checkAndSetUnhiddenLaunchIfNeeded - unhiddenLaunchOnReboot saved: true")
        }
        Log.d("BootWorker", "BootWorker: checkAndSetUnhiddenLaunchIfNeeded - END")
    }
}
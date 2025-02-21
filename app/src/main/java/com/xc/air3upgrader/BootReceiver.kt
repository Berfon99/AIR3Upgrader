package com.xc.air3upgrader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BootReceiver", "BootReceiver: onReceive called")
        val dataStoreManager = DataStoreManager(context)
        val isAutomaticUpgradeReminderEnabled: Boolean = runBlocking {
            dataStoreManager.getAutomaticUpgradeReminder().first()
        }
        val unhiddenLaunchOnReboot: Boolean = runBlocking {
            dataStoreManager.getUnhiddenLaunchOnReboot().first()
        }
        Log.d("BootReceiver", "BootReceiver: isAutomaticUpgradeReminderEnabled: $isAutomaticUpgradeReminderEnabled")
        Log.d("BootReceiver", "BootReceiver: unhiddenLaunchOnReboot: $unhiddenLaunchOnReboot")
        if (isAutomaticUpgradeReminderEnabled) {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    Log.d("BootReceiver", "BootReceiver: ACTION_BOOT_COMPLETED or ACTION_LOCKED_BOOT_COMPLETED received")
                    scheduleBootWork(context, unhiddenLaunchOnReboot)
                }
            }
        }
        Log.d("BootReceiver", "BootReceiver: onReceive - END")
    }

    private fun scheduleBootWork(context: Context, unhiddenLaunchOnReboot: Boolean) {
        Log.d("BootReceiver", "BootReceiver: Scheduling BootWorker")
        val workManager = WorkManager.getInstance(context)
        val bootWorkRequest = OneTimeWorkRequestBuilder<BootWorker>()
            .addTag("BootWorker")
            .build()
        workManager.enqueue(bootWorkRequest)
    }
}

class BootWorker(appContext: Context, workerParams: WorkerParameters) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        Log.d("BootWorker", "BootWorker: doWork called")
        val dataStoreManager = DataStoreManager(applicationContext)
        val unhiddenLaunchOnReboot: Boolean = runBlocking {
            dataStoreManager.getUnhiddenLaunchOnReboot().first()
        }
        val isAutomaticUpgradeReminderEnabled: Boolean = runBlocking {
            dataStoreManager.getAutomaticUpgradeReminder().first()
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
            // Schedule the upgrade check directly from here
            if (isAutomaticUpgradeReminderEnabled) {
                scheduleUpgradeCheck(applicationContext)
            }
        }
        Log.d("BootWorker", "BootWorker: doWork - END")
        return Result.success()
    }

    private fun scheduleUpgradeCheck(context: Context) {
        // Implement your upgrade check scheduling logic here
        // This is where you would use WorkManager to schedule the upgrade check
        Log.d("BootWorker", "BootWorker: Scheduling upgrade check")
    }
}
package com.xc.air3upgrader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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
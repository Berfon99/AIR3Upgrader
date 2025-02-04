package com.xc.air3upgrader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("BootReceiver: onReceive called")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Timber.d("BootReceiver: ACTION_BOOT_COMPLETED received")
            val dataStoreManager = DataStoreManager(context)
            val isAutomaticUpgradeReminderEnabled =
                runBlocking { dataStoreManager.getAutomaticUpgradeReminder().first() }
            Timber.d("BootReceiver: isAutomaticUpgradeReminderEnabled: $isAutomaticUpgradeReminderEnabled")
            if (isAutomaticUpgradeReminderEnabled) {
                // Set IS_MANUAL_LAUNCH to false for automatic launch
                runBlocking {
                    dataStoreManager.saveIsManualLaunch(false)
                }
                val shouldLaunchOnReboot =
                    runBlocking { dataStoreManager.getUnhiddenLaunchOnReboot().first() }
                Timber.d("BootReceiver: shouldLaunchOnReboot: $shouldLaunchOnReboot")
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                if (shouldLaunchOnReboot) {
                    Timber.d("BootReceiver: Launching MainActivity unhidden")
                } else {
                    Timber.d("BootReceiver: Launching MainActivity hidden")
                }
                context.startActivity(launchIntent)
                // Reset the flag after launching
                runBlocking {
                    dataStoreManager.saveUnhiddenLaunchOnReboot(false)
                }
            } else {
                Timber.d("BootReceiver: Automatic Upgrade Reminder is disabled, not launching MainActivity")
            }
        }
        Timber.d("BootReceiver: onReceive - END")
    }
}
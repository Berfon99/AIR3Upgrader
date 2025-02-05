package com.xc.air3upgrader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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
        if (isAutomaticUpgradeReminderEnabled && unhiddenLaunchOnReboot) {
            when (intent.action) {
                Intent.ACTION_BOOT_COMPLETED -> {
                    Log.d("BootReceiver", "BootReceiver: ACTION_BOOT_COMPLETED received")
                    launchBootService(context)
                }
                Intent.ACTION_LOCKED_BOOT_COMPLETED -> {
                    Log.d("BootReceiver", "BootReceiver: ACTION_LOCKED_BOOT_COMPLETED received")
                    launchBootService(context)
                }
            }
        }
        Log.d("BootReceiver", "BootReceiver: onReceive - END")
    }

    private fun launchBootService(context: Context) {
        Log.d("BootReceiver", "BootReceiver: Launching BootService")
        val serviceIntent = Intent(context, BootService::class.java)
        context.startService(serviceIntent)
    }
}
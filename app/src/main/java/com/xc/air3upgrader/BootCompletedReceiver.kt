package com.xc.air3upgrader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.TimeUnit

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("BootCompletedReceiver: onReceive called")
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Reschedule the UpgradeCheckWorker
            val dataStoreManager = DataStoreManager(context)
            runBlocking {
                val interval = dataStoreManager.getUpgradeCheckInterval().firstOrNull() ?: Interval(0, 0, 0)
                val periodicWorkRequest = PeriodicWorkRequest.Builder(
                    UpgradeCheckWorker::class.java,
                    interval.days.toLong(),
                    TimeUnit.DAYS
                )
                    .setInitialDelay(
                        interval.hours.toLong(),
                        TimeUnit.HOURS
                    )
                    .addTag("UpgradeCheck")
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "UpgradeCheck",
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWorkRequest
                )
            }
        }
    }
}
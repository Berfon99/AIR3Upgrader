package com.xc.air3upgrader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
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
                val unhiddenLaunchOnReboot =
                    runBlocking { dataStoreManager.getUnhiddenLaunchOnReboot().first() }
                Timber.d("BootReceiver: unhiddenLaunchOnReboot: $unhiddenLaunchOnReboot")
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    launchIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                if (unhiddenLaunchOnReboot) {
                    Timber.d("BootReceiver: Launching MainActivity unhidden")
                    try {
                        pendingIntent.send()
                    } catch (e: PendingIntent.CanceledException) {
                        Timber.e(e, "PendingIntent canceled")
                    }
                } else {
                    Timber.d("BootReceiver: Launching MainActivity hidden")
                    createNotificationChannel(context)
                    val builder = NotificationCompat.Builder(context, "channel_id")
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("App launched hidden")
                        .setContentText("App launched hidden")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                    with(NotificationManagerCompat.from(context)) {
                        notify(1, builder.build())
                    }
                }
                // Reset the flag after launching
                runBlocking {
                    dataStoreManager.saveUnhiddenLaunchOnReboot(false)
                }
                val checkAppRunningRequest = OneTimeWorkRequestBuilder<CheckAppRunningWorker>().build()
                WorkManager.getInstance(context).enqueue(checkAppRunningRequest)
            } else {
                Timber.d("BootReceiver: Automatic Upgrade Reminder is disabled, not launching MainActivity")
            }
        }
        Timber.d("BootReceiver: onReceive - END")
    }
    private fun createNotificationChannel(context: Context) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "channel_name"
            val descriptionText = "channel_description"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel("channel_id", name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
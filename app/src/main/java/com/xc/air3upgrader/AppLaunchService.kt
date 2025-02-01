package com.xc.air3upgrader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import timber.log.Timber

class AppLaunchService : Service() {

    companion object {
        private const val CHANNEL_ID = "AppLaunchServiceChannel"
        private const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()
        Timber.d("AppLaunchService: onCreate called")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("AppLaunchService: onStartCommand called")
        startForeground(NOTIFICATION_ID, createNotification())
        // Your code to check for upgrades goes here
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        Timber.d("AppLaunchService: createNotificationChannel called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "App Launch Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        Timber.d("AppLaunchService: createNotification called")
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("App Launch Service")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your icon
            .build()
    }
}
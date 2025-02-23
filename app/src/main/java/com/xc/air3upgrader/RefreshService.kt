package com.xc.air3upgrader

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import timber.log.Timber

class RefreshService : Service() {

    override fun onCreate() {
        super.onCreate()
        Timber.d("RefreshService: onCreate called")

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, "default")
            .setContentTitle("AIR3 Upgrader")
            .setContentText("Listening for package installations")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("RefreshService: onDestroy called")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
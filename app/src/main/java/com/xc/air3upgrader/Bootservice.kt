package com.xc.air3upgrader

import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log

class BootService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val delayMillis = 5000L // 5 seconds delay

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BootService", "BootService: onStartCommand called")
        handler.postDelayed({
            launchMainActivity(this)
            stopSelf() // Stop the service after launching MainActivity
        }, delayMillis)
        return START_NOT_STICKY
    }

    private fun launchMainActivity(context: Context) {
        Log.d("BootService", "BootService: Launching MainActivity")
        if (Settings.canDrawOverlays(context)) {
            val mainActivityIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(mainActivityIntent)
        } else {
            Log.d("BootService", "BootService: Display over other apps permission not granted")
            // Request the permission
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }
}
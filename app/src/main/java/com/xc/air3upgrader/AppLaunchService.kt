package com.xc.air3upgrader

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.Calendar

class AppLaunchService : Service() {

    private lateinit var dataStoreManager: DataStoreManager

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("AppLaunchService: onStartCommand called")
        if (checkTimeElapsed()) {
            launchApp()
            resetTimer()
        }
        // Stop the service after launching the activity
        stopSelf()

        return START_NOT_STICKY
    }

    private fun checkTimeElapsed(): Boolean {
        Timber.d("AppLaunchService: checkTimeElapsed called")
        return runBlocking {
            val interval = dataStoreManager.getUpgradeCheckInterval().firstOrNull() ?: Interval(0, 0, 0)
            val lastCheckTime = dataStoreManager.getLastCheckTime().firstOrNull() ?: 0L
            val currentTime = Calendar.getInstance().timeInMillis

            val intervalMillis = (interval.days * 24 * 60 * 60 * 1000L) + (interval.hours * 60 * 60 * 1000L) + (interval.minutes * 60 * 1000L)
            val timeElapsed = currentTime - lastCheckTime
            timeElapsed >= intervalMillis
        }
    }

    private fun launchApp() {
        Timber.d("AppLaunchService: launchApp called")
        // Launch the MainActivity
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
    }

    private fun resetTimer() {
        Timber.d("AppLaunchService: resetTimer called")
        runBlocking {
            dataStoreManager.saveLastCheckTime(Calendar.getInstance().timeInMillis)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
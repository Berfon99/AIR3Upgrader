package com.xc.air3upgrader

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import timber.log.Timber

class UpgradeCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("UpgradeCheckWorker: doWork called")
        // Start the AppLaunchService
        val serviceIntent = Intent(applicationContext, AppLaunchService::class.java)
        applicationContext.startForegroundService(serviceIntent)
        return Result.success()
    }
}
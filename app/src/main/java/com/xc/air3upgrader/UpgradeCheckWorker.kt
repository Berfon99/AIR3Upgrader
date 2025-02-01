package com.xc.air3upgrader

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import timber.log.Timber

class UpgradeCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        Timber.d("UpgradeCheckWorker: doWork called")
        val intent = Intent(applicationContext, AppLaunchService::class.java)
        applicationContext.startService(intent)
        return Result.success()
    }
}
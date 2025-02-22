package com.xc.air3upgrader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import timber.log.Timber

class PackageInstalledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("PackageInstalledReceiver: onReceive called")
        val action = intent.action
        if (action == Intent.ACTION_PACKAGE_ADDED) {
            Timber.d("Package installed")
            val installedPackageName = intent.data?.schemeSpecificPart
            Timber.d("Installed package: $installedPackageName")
            // Trigger the refresh action
            MainActivity.getInstance()?.continueSetup()
        }
    }
}

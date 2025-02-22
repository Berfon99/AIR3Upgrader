package com.xc.air3upgrader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class PackageInstalledReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("PackageInstalledReceiver: onReceive called")
        if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
            val installedPackageName = intent.data?.schemeSpecificPart
            Timber.d("Installed package: $installedPackageName")
            MainActivity.getInstance()?.continueSetup()
        }
    }
}


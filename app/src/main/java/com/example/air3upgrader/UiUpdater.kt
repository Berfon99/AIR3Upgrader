package com.example.air3upgrader

import android.content.Context
import android.widget.CheckBox
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UiUpdater {
    fun updateAppInfo(
        context: Context,
        packageName: String,
        nameTextView: TextView,
        versionTextView: TextView?,
        versionName: String?,
        versionCode: Long?
    ) {
        nameTextView.text = AppManager.getAppName(context, packageName)
        CoroutineScope(Dispatchers.Main).launch {
            AppUtils.setAppBackgroundColor(context, packageName, nameTextView, versionName ?: "N/A")
        }
        if (versionTextView != null) {
            if (packageName == "com.xc.r3") {
                versionTextView.text = "v$versionName ($versionCode)"
            } else {
                versionTextView.text = versionName ?: "N/A"
            }
        }
    }

    fun updateServerVersion(
        xctrackServerVersion: TextView,
        xcguideServerVersion: TextView,
        xctrackLatestVersion: String?,
        xcguideLatestVersion: String?
    ) {
        xctrackServerVersion.text = xctrackLatestVersion ?: "N/A"
        xcguideServerVersion.text = xcguideLatestVersion ?: "N/A"
    }

    fun updateCheckboxState(
        packageName: String,
        checkBox: CheckBox,
        installedVersion: String,
        serverVersion: String?
    ) {
        if (serverVersion != null) {
            checkBox.isChecked = VersionComparator.isServerVersionHigher(installedVersion, serverVersion, packageName)
        } else {
            checkBox.isChecked = false
        }
    }
}
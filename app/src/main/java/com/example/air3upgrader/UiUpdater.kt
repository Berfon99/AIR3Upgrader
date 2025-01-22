package com.example.air3upgrader

import android.content.Context
import android.widget.CheckBox
import android.widget.TextView
import com.example.air3upgrader.R.string.* // Import string resources
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
        versionCode: Long?,
        selectedModel: String? // Add selectedModel as a parameter
    ) {
        nameTextView.text = AppManager.getAppName(context, packageName)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                AppUtils.setAppBackgroundColor(context, packageName, nameTextView, versionName, selectedModel) // Pass selectedModel
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (versionTextView != null) {
            versionTextView.text = context.getString(server) + " " + (versionName ?: context.getString(na)) // Use string resources
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
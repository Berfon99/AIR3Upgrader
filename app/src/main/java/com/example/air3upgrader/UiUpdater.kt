package com.example.air3upgrader

import android.content.Context
import android.view.View
import android.app.Activity
import android.widget.CheckBox
import com.example.air3upgrader.R.string.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.widget.TextView
import android.os.Handler
import android.os.Looper
import androidx.glance.visibility

object UiUpdater {

    private fun updateApkNameDisplay(context: Context, appInfo: AppInfo, apkNameTextView: TextView?) {
        Handler(Looper.getMainLooper()).post {
            apkNameTextView?.text = appInfo.apkPath.substringAfterLast('/')
            apkNameTextView?.visibility = View.VISIBLE
        }
    }

    fun updateAppInfo(
        context: Context,
        appInfo: AppInfo,
        nameTextView: TextView,
        serverVersionTextView: TextView,
        installedVersionTextView: TextView?,
        selectedModel: String?,
        appInfos: List<AppInfo> // Add appInfos parameter
    ) {
        nameTextView.text = appInfo.name
        serverVersionTextView.text = context.getString(server) + " " + appInfo.latestVersion
        installedVersionTextView?.text = if (appInfo.installedVersion != context.getString(na)) context.getString(installed) + " " + appInfo.installedVersion else context.getString(not_installed)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                AppUtils.setAppBackgroundColor(context, appInfo.`package`, nameTextView, appInfo.installedVersion, selectedModel, appInfos) // Pass appInfos
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val apkNameTextView = when (appInfo.`package`) {
                "org.xcontest.XCTrack" -> (context as Activity).findViewById<TextView>(R.id.xctrack_apk_name)
                "indysoft.xc_guide" -> (context as Activity).findViewById<TextView>(R.id.xcguide_apk_name)
                "com.xc.r3" -> (context as Activity).findViewById<TextView>(R.id.air3manager_apk_name)
                else -> null
            }

            if (appInfo.isSelectedForUpgrade) {
                updateApkNameDisplay(context, appInfo, apkNameTextView)
            } else {
                apkNameTextView?.visibility = View.GONE
            }
        }
    }

    fun updateCheckboxState(context: Context, appInfo: AppInfo, checkBox: CheckBox, installedVersion: String) {
        checkBox.isChecked = VersionComparator.isServerVersionHigher(appInfo.installedVersion ?: "", appInfo.latestVersion, appInfo.`package`)
        //checkBox.isEnabled = appInfo.installedVersion != appInfo.latestVersion
    }
}
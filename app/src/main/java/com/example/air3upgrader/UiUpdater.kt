package com.example.air3upgrader

import android.content.Context
import android.view.View
import android.app.Activity
import android.widget.CheckBox
import com.example.air3upgrader.R.string.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext // Ajoutez cette importation

import android.widget.TextView
import android.os.Handler
import android.os.Looper

object UiUpdater {
    fun updateAppInfo(
        context: Context,
        appInfo: AppInfo,
        nameTextView: TextView,
        serverVersionTextView: TextView,
        installedVersionTextView: TextView?,
        selectedModel: String?
    ) {
        nameTextView.text = appInfo.name
        serverVersionTextView.text = context.getString(server) + " " + appInfo.latestVersion
        installedVersionTextView?.text = if (appInfo.installedVersion != context.getString(na)) context.getString(installed) + " " + appInfo.installedVersion else context.getString(not_installed)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                AppUtils.setAppBackgroundColor(context, appInfo.`package`, nameTextView, appInfo.installedVersion, selectedModel)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // The following block is commented out to remove the APK name display
            /*
            val apkFileNameTextView = when (appInfo.`package`) {
                "org.xcontest.XCTrack" -> (context as Activity).findViewById(R.id.xctrack_apk_name)
                "indysoft.xc_guide" -> (context as Activity).findViewById(R.id.xcguide_apk_name)
                "com.xc.r3" -> (context as Activity).findViewById(R.id.air3manager_apk_name)
                else -> null
            }

            val updateUiRunnable = Runnable { // Create a custom runnable
                val apkTextView = when (appInfo.`package`) {
                    "org.xcontest.XCTrack" -> (context as Activity).findViewById<TextView>(R.id.xctrack_apk_name)
                    "indysoft.xc_guide" -> (context as Activity).findViewById<TextView>(R.id.xcguide_apk_name)
                    "com.xc.r3" -> (context as Activity).findViewById<TextView>(R.id.air3manager_apk_name)
                    else -> null
                }
                apkTextView?.text = appInfo.apkPath.substringAfterLast('/')
                apkTextView?.visibility = View.VISIBLE
            }
            Handler(Looper.getMainLooper()).post(updateUiRunnable) // Post the runnable
            */
        }
    }

    fun updateCheckboxState(context: Context, appInfo: AppInfo, checkBox: CheckBox, installedVersion: String) {
        checkBox.isChecked = VersionComparator.isServerVersionHigher(appInfo.installedVersion ?: "", appInfo.latestVersion, appInfo.`package`)
        //checkBox.isEnabled = appInfo.installedVersion != appInfo.latestVersion
    }
}
package com.example.air3upgrader

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.glance.visibility

object UiUpdater {
    internal fun updateApkNameDisplay(context: Context, appInfo: AppInfo, apkNameTextView: TextView?) {
        apkNameTextView?.text = appInfo.apkPath.substringAfterLast('/')
        apkNameTextView?.visibility = View.VISIBLE
    }

    fun updateAppInfo(
        context: Context,
        appInfo: AppInfo,
        nameTextView: TextView,
        serverVersionTextView: TextView,
        installedVersionTextView: TextView?,
        selectedModel: String?
    ) {
        nameTextView.text = appInfo.name
        serverVersionTextView.text = context.getString(R.string.server) + " " + appInfo.latestVersion
        val installedVersion = getInstalledVersion(context, appInfo.`package`)
        Log.d("UiUpdater", "Installed version for ${appInfo.`package`}: $installedVersion")
        installedVersionTextView?.text =
            if (installedVersion != null) context.getString(R.string.installed) + " " + installedVersion else context.getString(R.string.not_installed)
        setAppBackgroundColor(context, appInfo, nameTextView, installedVersionTextView)
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

    private fun getInstalledVersion(context: Context, packageName: String): String? {
        Log.d("UiUpdater", "getInstalledVersion() called for package: $packageName")
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            Log.d("UiUpdater", "Raw version name for $packageName: $versionName")
            val filteredVersion = AppUtils.getAppVersion(context, packageName)
            Log.d("UiUpdater", "Filtered version for $packageName: $filteredVersion")
            filteredVersion
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("UiUpdater", "Package not found: $packageName", e)
            null
        } catch (e: Exception) {
            Log.e("UiUpdater", "Error getting version for $packageName", e)
            null
        }
    }

    internal fun setAppBackgroundColor(
        context: Context,
        appInfo: AppInfo,
        nameTextView: TextView,
        installedVersionTextView: TextView?
    ) {
        val installedVersion = getInstalledVersion(context, appInfo.`package`)
        val filteredServerVersion = appInfo.latestVersion
        Log.d("UiUpdater", "setAppBackgroundColor() called for ${appInfo.name}")
        Log.d("UiUpdater", "  Installed Version: $installedVersion")
        Log.d("UiUpdater", "  Filtered Server Version: $filteredServerVersion")
        Log.d("UiUpdater", "  installedVersionTextView?.text: ${installedVersionTextView?.text}")

        val color = if (installedVersionTextView?.text == context.getString(R.string.not_installed)) {
            Log.d("UiUpdater", "  Color: Not Installed")
            ContextCompat.getColor(context, R.color.not_installed_color)
        } else if (filteredServerVersion == installedVersion) {
            Log.d("UiUpdater", "  Color: Up-to-Date")
            ContextCompat.getColor(context, R.color.up_to_date_color)
        } else {
            Log.d("UiUpdater", "  Color: Update Available")
            ContextCompat.getColor(context, R.color.update_available_color)
        }
        nameTextView.setBackgroundColor(color)
    }
}
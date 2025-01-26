package com.example.air3upgrader

import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.app.Activity
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.glance.visibility

object UiUpdater {
    private fun updateApkNameDisplay(context: Context, appInfo: AppInfo, apkNameTextView: TextView?) {
        apkNameTextView?.text = appInfo.apkPath.substringAfterLast('/')
        apkNameTextView?.visibility = View.VISIBLE
    }
    fun updateAppInfo(context: Context, appInfo: AppInfo, nameTextView: TextView, serverVersionTextView: TextView, installedVersionTextView: TextView?, selectedModel: String?) {
        nameTextView.text = appInfo.name
        serverVersionTextView.text = context.getString(R.string.server) + " " + appInfo.latestVersion
        installedVersionTextView?.text = if (getInstalledVersion(context, appInfo.`package`) != null) context.getString(R.string.installed) + " " + getInstalledVersion(context, appInfo.`package`) else context.getString(R.string.not_installed)
        setAppBackgroundColor(context, appInfo, nameTextView, installedVersionTextView)
        val apkNameTextView = when (appInfo.`package`) {
            "org.xcontest.XCTrack" -> (context as Activity).findViewById<TextView>(R.id.xctrack_apk_name)
            "indysoft.xc_guide" -> (context as Activity).findViewById<TextView>(R.id.xcguide_apk_name)
            "com.air3.air3manager" -> (context as Activity).findViewById<TextView>(R.id.air3manager_apk_name)
            else -> null
        }
        if (appInfo.isSelectedForUpgrade) {
            updateApkNameDisplay(context, appInfo, apkNameTextView)
        } else {
            apkNameTextView?.visibility = View.GONE
        }
    }

    private fun getInstalledVersion(context: Context, packageName: String): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    private fun setAppBackgroundColor(context: Context, appInfo: AppInfo, nameTextView: TextView, installedVersionTextView: TextView?) {
        val color = if (installedVersionTextView?.text == context.getString(R.string.not_installed)) {
            ContextCompat.getColor(context, R.color.not_installed_color)
        } else if (appInfo.latestVersion == getInstalledVersion(context, appInfo.`package`)) {
            ContextCompat.getColor(context, R.color.up_to_date_color)
        } else {
            ContextCompat.getColor(context, R.color.update_available_color)
        }
        nameTextView.setBackgroundColor(color)
    }
}
package com.xc.air3upgrader

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import timber.log.Timber

object UiUpdater {
    private fun updateApkNameDisplay(appInfo: AppInfo, apkNameTextView: TextView?) {
        Timber.d("updateApkNameDisplay() called for app: ${appInfo.name}")
        val apkName = appInfo.apkPath.substringAfterLast('/')
        Timber.d("APK name: $apkName")
        apkNameTextView?.text = apkName
        apkNameTextView?.visibility = View.VISIBLE
    }

    fun updateAppInfo(
        context: Context,
        appInfo: AppInfo,
        nameTextView: TextView,
        serverVersionTextView: TextView,
        installedVersionTextView: TextView?
    ) {
        Timber.d("updateAppInfo() called for app: ${appInfo.name}")
        nameTextView.text = appInfo.name
        serverVersionTextView.text = context.getString(R.string.server_version, appInfo.highestServerVersion)
        val installedVersion = getInstalledVersion(context, appInfo.`package`)
        Timber.d("Installed version for ${appInfo.`package`}: $installedVersion")
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
            updateApkNameDisplay(appInfo, apkNameTextView)
        } else {
            apkNameTextView?.visibility = View.GONE
        }
    }

    private fun getInstalledVersion(context: Context, packageName: String): String? {
        Timber.d("getInstalledVersion() called for package: $packageName")
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            Timber.d("Raw version name for $packageName: $versionName")
            val filteredVersion = AppUtils.getAppVersion(context, packageName)
            Timber.d("Filtered version for $packageName: $filteredVersion")
            filteredVersion
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e, "Package not found: $packageName")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error getting version for $packageName")
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
        Timber.d("setAppBackgroundColor() called for ${appInfo.name}")
        Timber.d("  Installed Version: $installedVersion")
        Timber.d("  Highest Server Version: ${appInfo.highestServerVersion}")
        Timber.d("  installedVersionTextView?.text: ${installedVersionTextView?.text}")

        val color = if (installedVersionTextView?.text == context.getString(R.string.not_installed)) {
            Timber.d("  Color: Not Installed")
            ContextCompat.getColor(context, R.color.not_installed_color)
        } else if (!VersionComparator.isServerVersionHigher(installedVersion ?: "", appInfo.highestServerVersion, appInfo.`package`)) {
            Timber.d("  Color: Up-to-Date")
            ContextCompat.getColor(context, R.color.up_to_date_color)
        } else {
            Timber.d("  Color: Update Available")
            ContextCompat.getColor(context, R.color.update_available_color)
        }
        nameTextView.setBackgroundColor(color)
    }
}
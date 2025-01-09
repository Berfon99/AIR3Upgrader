package com.example.air3upgrader

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object AppUtils {
    fun getAppVersion(context: Context, packageName: String): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "N/A" // Use the elvis operator to provide a default value
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MainActivity", "Error getting app version", e)
            "N/A"
        }
    }

    fun getPackageNameFromApk(context: Context, apkFile: File): String? {
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            packageInfo?.packageName
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting package name from APK", e)
            null
        }
    }


    suspend fun setAppBackgroundColor(context: Context, packageName: String, appName: TextView, installedVersion: String, selectedModel: String) {
        val serverAppInfo = VersionChecker().getLatestVersionFromServer(selectedModel).find { it.packageName == packageName }
        val serverVersion = serverAppInfo?.latestVersion ?: "N/A"
        val isHigher = VersionComparator.isServerVersionHigher(installedVersion, serverVersion, packageName)
        val backgroundDrawable = when {
            isHigher -> ContextCompat.getDrawable(context, R.drawable.circle_background_orange)
            installedVersion == "N/A" -> ContextCompat.getDrawable(context, R.drawable.circle_background_black)
            else -> ContextCompat.getDrawable(context, R.drawable.circle_background_green)
        }
        appName.background = backgroundDrawable
    }
}
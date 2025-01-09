package com.example.air3upgrader

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppUtils {
    fun getAppVersion(context: Context, packageName: String): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    suspend fun setAppBackgroundColor(context: Context, packageName: String, nameTextView: TextView, installedVersion: String?, selectedModel: String?) {
        val serverVersion = getServerVersion(packageName, selectedModel ?: "")
        if (serverVersion != null && installedVersion != null) {
            if (VersionComparator.isServerVersionHigher(installedVersion, serverVersion, packageName)) {
                nameTextView.setBackgroundColor(ContextCompat.getColor(context, R.color.update_available))
            } else {
                nameTextView.setBackgroundColor(Color.TRANSPARENT)
            }
        } else {
            nameTextView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private suspend fun getServerVersion(packageName: String, selectedModel: String): String? = withContext(Dispatchers.IO) {
        val appInfos = VersionChecker().getLatestVersionFromServer(selectedModel)
        return@withContext appInfos.find { it.packageName == packageName }?.latestVersion
    }
}

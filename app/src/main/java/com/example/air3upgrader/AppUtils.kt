package com.example.air3upgrader

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppUtils {
    fun getAppVersion(context: Context, packageName: String): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val fullVersionName = packageInfo.versionName

            if (fullVersionName != null) {
                when (packageName) {
                    "org.xcontest.XCTrack" -> {
                        // Split by . and - and take the first 5 parts
                        val parts = fullVersionName.split(".", "-").take(5)
                        // Reconstruct the version string with the original separators
                        val reconstructedVersion = buildString {
                            var partIndex = 0
                            var charIndex = 0
                            while (partIndex < parts.size && charIndex < fullVersionName.length) {
                                val currentPart = parts[partIndex]
                                append(currentPart)
                                charIndex += currentPart.length
                                partIndex++
                                if (partIndex < parts.size) {
                                    while (charIndex < fullVersionName.length && fullVersionName[charIndex] != '.' && fullVersionName[charIndex] != '-') {
                                        charIndex++
                                    }
                                    if (charIndex < fullVersionName.length) {
                                        append(fullVersionName[charIndex])
                                        charIndex++
                                    }
                                }
                            }
                        }
                        reconstructedVersion
                    }
                    "indysoft.xc_guide" -> {
                        // Remove everything before the last dot
                        fullVersionName.substringAfterLast(".")
                    }
                    "com.xc.r3" -> {
                        // Remove everything after the last dot
                        fullVersionName.substringBeforeLast(".")
                    }
                    else -> fullVersionName // Return the full version for other apps
                }
            } else {
                "N/A" // Handle the case where versionName is null
            }
        } catch (e: PackageManager.NameNotFoundException) {
            "N/A"
        }
    }

    suspend fun setAppBackgroundColor(context: Context, packageName: String, nameTextView: TextView, installedVersion: String?, selectedModel: String?) {
        val serverVersion = getServerVersion(packageName, selectedModel)
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

    private suspend fun getServerVersion(packageName: String, selectedModel: String?): String? = withContext(Dispatchers.IO) {
        val appInfos = VersionChecker().getLatestVersionFromServer(selectedModel ?: "")
        return@withContext appInfos.find { it.packageName == packageName }?.latestVersion
    }
}
package com.example.air3upgrader

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object AppUtils {

    fun getAppVersion(context: Context, packageName: String): String {
        Log.d("AppUtils", "getAppVersion() called for package: $packageName")
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: context.getString(R.string.na)
            Log.d("AppUtils", "Raw version name for $packageName: $versionName")
            val filteredVersion = filterVersion(versionName, packageName)
            Log.d("AppUtils", "Filtered version for $packageName: $filteredVersion")
            filteredVersion
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("AppUtils", "Package not found: $packageName", e)
            context.getString(R.string.na)
        } catch (e: Exception) {
            Log.e("AppUtils", "Error getting version for $packageName", e)
            context.getString(R.string.na)
        }
    }

    private fun filterVersion(versionName: String, packageName: String): String {
        Log.d("AppUtils", "filterVersion() called for package: $packageName, versionName: $versionName")
        return when (packageName) {
            "org.xcontest.XCTrack" -> {
                val parts = versionName.split("-")
                if (parts.isNotEmpty()) {
                    parts[0]
                } else {
                    versionName
                }
            }

            "indysoft.xc_guide" -> {
                val parts = versionName.split(".")
                if (parts.size > 1) {
                    parts[1]
                } else {
                    versionName
                }
            }

            "com.xc.r3" -> {
                val parts = versionName.split(".")
                if (parts.size > 1) {
                    parts[0] + "." + parts[1]
                } else {
                    versionName
                }
            }

            else -> versionName
        }
    }

    suspend fun getServerVersion(context: Context, packageName: String, selectedModel: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://ftp.fly-air3.com/versions/$selectedModel/$packageName.txt")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000 // 5 seconds
                connection.readTimeout = 5000 // 5 seconds
                connection.requestMethod = "GET"

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val serverVersion = inputStream.bufferedReader().use { it.readText() }.trim()
                    inputStream.close()
                    serverVersion
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun setAppBackgroundColor(context: Context, packageName: String, appNameTextView: TextView, installedVersion: String, selectedModel: String) {
        val serverVersion = getServerVersion(context, packageName, selectedModel)
        val color = if (installedVersion == context.getString(R.string.na)) {
            ContextCompat.getColor(context, R.color.not_installed_color)
        } else if (serverVersion == installedVersion) {
            ContextCompat.getColor(context, R.color.up_to_date_color)
        } else {
            ContextCompat.getColor(context, R.color.update_available_color)
        }
        appNameTextView.setBackgroundColor(color)
    }
}
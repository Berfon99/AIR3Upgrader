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
                val parts = versionName.replace("-", ".").split(".") // Replace "-" with "." and split
                Log.d("AppUtils", "Parts after replace and split: $parts")
                val filteredParts = parts.take(5) // Take up to 5 parts
                Log.d("AppUtils", "Parts after take: $filteredParts")
                val paddedParts = filteredParts + List(5 - filteredParts.size) { "0" } // Pad with "0" if needed
                Log.d("AppUtils", "Parts after padding: $paddedParts")
                paddedParts.joinToString(".") // Join with "."
            }
            "indysoft.xc_guide" -> {
                val parts = versionName.split(".")
                Log.d("AppUtils", "Parts after split: $parts")
                if (parts.size > 1) {
                    val version = parts[1]
                    Log.d("AppUtils", "Version after filtering: $version")
                    version
                } else {
                    Log.d("AppUtils", "No filtering needed, returning: $versionName")
                    versionName
                }
            }
            "com.xc.r3" -> {
                val parts = versionName.split(".")
                Log.d("AppUtils", "Parts after split: $parts")
                if (parts.size > 1) {
                    val version = parts[0] + "." + parts[1]
                    Log.d("AppUtils", "Version after filtering: $version")
                    version
                } else {
                    Log.d("AppUtils", "No filtering needed, returning: $versionName")
                    versionName
                }
            }
            else -> {
                Log.d("AppUtils", "No filtering needed, returning: $versionName")
                versionName
            }
        }
    }

    suspend fun getServerVersion(context: Context, packageName: String, selectedModel: String): String? {
        Log.d("AppUtils", "getServerVersion() called for package: $packageName, selectedModel: $selectedModel")
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://ftp.fly-air3.com/versions/$selectedModel/$packageName.txt")
                Log.d("AppUtils", "URL: $url")
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000 // 5 seconds
                connection.readTimeout = 5000 // 5 seconds
                connection.requestMethod = "GET"
                Log.d("AppUtils", "Connection request method: ${connection.requestMethod}")

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    Log.d("AppUtils", "Connection response code: ${connection.responseCode}")
                    val inputStream = connection.inputStream
                    val serverVersion = inputStream.bufferedReader().use { it.readText() }.trim()
                    inputStream.close()
                    Log.d("AppUtils", "Server version: $serverVersion")
                    serverVersion
                } else {
                    Log.e("AppUtils", "Connection response code: ${connection.responseCode}")
                    null
                }
            } catch (e: Exception) {
                Log.e("AppUtils", "Error getting server version", e)
                null
            }
        }
    }

    suspend fun setAppBackgroundColor(context: Context, packageName: String, appNameTextView: TextView, installedVersion: String, selectedModel: String) {
        Log.d("AppUtils", "setAppBackgroundColor() called for package: $packageName, installedVersion: $installedVersion, selectedModel: $selectedModel")
        val serverVersion = getServerVersion(context, packageName, selectedModel)
        Log.d("AppUtils", "Server version: $serverVersion")
        val color = if (installedVersion == context.getString(R.string.na)) {
            Log.d("AppUtils", "App not installed, setting not_installed_color")
            ContextCompat.getColor(context, R.color.not_installed_color)
        } else if (serverVersion == installedVersion) {
            Log.d("AppUtils", "App up to date, setting up_to_date_color")
            ContextCompat.getColor(context, R.color.up_to_date_color)
        } else {
            Log.d("AppUtils", "Update available, setting update_available_color")
            ContextCompat.getColor(context, R.color.update_available_color)
        }
        appNameTextView.setBackgroundColor(color)
        Log.d("AppUtils", "Background color set")
    }
}
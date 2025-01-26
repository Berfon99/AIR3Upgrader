package com.example.air3upgrader

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.HttpURLConnection

object AppUtils {

    fun getAppVersion(context: Context, packageName: String): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: context.getString(R.string.na)
        } catch (e: PackageManager.NameNotFoundException) {
            context.getString(R.string.na)
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
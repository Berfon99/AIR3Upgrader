package com.example.air3upgrader

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.lang.reflect.Type

data class AppInfo(
    val name: String,
    val `package`: String,
    val latestVersion: String,
    val apkPath: String,
    val compatibleModels: List<String>,
    val minAndroidVersion: String
)

data class AppsData(
    val apps: List<AppInfo>
)

class VersionChecker {

    private val client = OkHttpClient()
    private val gson = Gson()
    private val versionsUrl = "https://ftp.fly-air3.com/Latest_Software_Download/versions.json"

    fun getLatestVersion(packageName: String): String? {
        val appsData = getAppsData() ?: return null
        val appInfo = appsData.apps.find { it.`package` == packageName } ?: return null
        return appInfo.latestVersion
    }

    private fun getAppsData(): AppsData? {
        val request = Request.Builder()
            .url(versionsUrl)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val body = response.body?.string() ?: return null
                val listType: Type = object : TypeToken<AppsData>() {}.type
                return gson.fromJson(body, listType)
            }
        } catch (e: IOException) {
            Log.e("VersionChecker", "Error getting versions.json", e)
            return null
        }
    }
}
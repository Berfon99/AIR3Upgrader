package com.example.air3upgrader

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
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

    fun getAppInfo(packageName: String): AppInfo? {
        val appsData = getAppsData() ?: return null
        return appsData.apps.find { it.`package` == packageName }
    }

    fun isServerVersionHigher(installedVersion: String?, serverVersion: String?, packageName: String): Boolean {
        if (installedVersion == null || serverVersion == null) {
            return false
        }
        val installedParts = when (packageName) {
            "org.xcontest.XCTrack" -> installedVersion.split(".", "-")
            "indysoft.xc_guide" -> listOf(installedVersion)
            else -> listOf(installedVersion)
        }
        val serverParts = serverVersion.split(".")

        val maxLength = maxOf(installedParts.size, serverParts.size)

        for (i in 0 until maxLength) {
            val installedPart = installedParts.getOrElse(i) { "0" }.toIntOrNull() ?: 0
            val serverPart = serverParts.getOrElse(i) { "0" }.toIntOrNull() ?: 0

            if (serverPart > installedPart) {
                return true
            } else if (serverPart < installedPart) {
                return false
            }
        }
        return false
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

    fun downloadApk(appInfo: AppInfo, apkFile: File) {
        val apkUrl = appInfo.apkPath
        val request = Request.Builder()
            .url(apkUrl)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to download APK: ${response.code}")

                response.body?.byteStream()?.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("VersionChecker", "Error downloading APK", e)
            throw e
        }
    }
}
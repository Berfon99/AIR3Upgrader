package com.example.air3upgrader

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.lang.reflect.Type

class VersionChecker {

    val client = OkHttpClient()
    private val gson = Gson()
    private val versionsUrl = "https://ftp.fly-air3.com/Latest_Software_Download/versions.json"

    suspend fun getLatestVersion(packageName: String): String? {
        val appsData = getAppsData() ?: return null
        val appInfo = appsData.apps.find { it.`package` == packageName } ?: return null
        return appInfo.latestVersion
    }

    suspend fun getAppInfo(packageName: String): AppInfo? {
        val appsData = getAppsData() ?: return null
        return appsData.apps.find { it.`package` == packageName }
    }

    private suspend fun getAppsData(): AppsData? {
        val request = Request.Builder()
            .url(versionsUrl)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val body = response.body?.string() ?: return@withContext null
                val listType: Type = object : TypeToken<AppsData>() {}.type
                return@withContext gson.fromJson(body, listType)
            } catch (e: IOException) {
                Log.e("VersionChecker", "Error getting versions.json", e)
                return@withContext null
            }
        }
    }

    fun downloadApk(appInfo: AppInfo, apkFile: File) {
        ApkDownloader.downloadApk(client, appInfo, apkFile)
    }
}
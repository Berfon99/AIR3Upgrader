package com.example.air3upgrader

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.glance.layout.size
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class VersionChecker(private val context: Context) {

    private fun downloadJson(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 5000 // 5 seconds
        connection.readTimeout = 5000 // 5 seconds
        connection.requestMethod = "GET"

        if (connection.responseCode == HttpURLConnection.HTTP_OK) {
            val inputStream = connection.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val response = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                response.append(line)
            }
            reader.close()
            inputStream.close()
            connection.disconnect()
            return response.toString()
        } else {
            throw Exception("HTTP error code: ${connection.responseCode}")
        }
    }

    suspend fun getLatestVersionFromServer(selectedModel: String): List<AppInfo> = withContext(Dispatchers.IO) {
        Log.d("VersionChecker", "getLatestVersionFromServer() called with selectedModel: $selectedModel")
        try {
            val jsonString = downloadJson("https://ftp.fly-air3.com/Latest_Software_Download/versions.json")
            Log.d("VersionChecker", "Raw server response: $jsonString")
            val gson = GsonBuilder().create()
            val listType = object : TypeToken<AppsData>() {}.type
            val appsData: AppsData = gson.fromJson(jsonString, listType)
            val appInfos = appsData.apps

            val filteredAppInfos = appInfos.filter { appInfo ->
                if (appInfo.`package` == "com.xc.r3") {
                    val isModelCompatible = appInfo.compatibleModels.contains(selectedModel)
                    val isAndroidVersionCompatible = Build.VERSION.SDK_INT >= appInfo.minAndroidVersion.toInt()
                    isModelCompatible && isAndroidVersionCompatible
                } else {
                    true // Keep other apps
                }
            }.toMutableList()

            // Find the first matching AIR³ Manager entry and add it to the list
            val air3ManagerInfo = appInfos.filter { it.`package` == "com.xc.r3" && it.compatibleModels.contains(selectedModel) && Build.VERSION.SDK_INT >= it.minAndroidVersion.toInt() }.maxByOrNull { it.latestVersion }
            if (air3ManagerInfo != null) {
                filteredAppInfos.removeAll { it.`package` == "com.xc.r3" }
                filteredAppInfos.add(0, air3ManagerInfo) // Add at the beginning
            }

            // Update installedVersion and highestServerVersion for each AppInfo
            filteredAppInfos.onEach { appInfo ->
                appInfo.installedVersion = AppUtils.getAppVersion(context, appInfo.`package`)
                appInfo.highestServerVersion = appInfo.latestVersion
                Log.d("VersionChecker", "Setting highestServerVersion for ${appInfo.`package`} to ${appInfo.highestServerVersion}") // Added log
            }
            Log.d("VersionChecker", "Successfully fetched ${filteredAppInfos.size} app infos from server")
            for (appInfo in filteredAppInfos) {
                Log.d("VersionChecker", "AppInfo: ${appInfo.name}, Package: ${appInfo.`package`}, APK Path: ${appInfo.apkPath}, Highest Server Version: ${appInfo.highestServerVersion}")
            }
            return@withContext filteredAppInfos
        } catch (e: Exception) {
            Log.e("VersionChecker", "Error getting latest version from server", e)
            return@withContext emptyList()
        }
    }

    private fun isAndroidVersionCompatible(minAndroidVersion: String): Boolean {
        val currentVersion = Build.VERSION.RELEASE
        Log.d("VersionChecker", "Current Android version: $currentVersion, Min Android version: $minAndroidVersion")
        return currentVersion.compareTo(minAndroidVersion) >= 0
    }

    private fun JSONArray.toList(): List<String> {
        return (0 until length()).map { getString(it) }
    }
}
package com.xc.air3upgrader

import android.content.Context
import android.os.Build
import android.util.Log
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

    private fun getApiLevelFromAndroidVersion(androidVersion: String): Int {
        return when (androidVersion) {
            "5" -> 21 // Lollipop
            "8" -> 26 // Oreo
            "8.1" -> 27 // Oreo MR1
            "10" -> 29 // Android 10
            "11" -> 30 // Android 11
            "13" -> 33 // Android 13
            "14" -> 34 // Android 14
            "15" -> 35 // Android 15
            else -> 0 // Unknown version
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
            var appInfos = appsData.apps

            // Filter AIR³ Manager entries based on compatibility
            var compatibleAir3ManagerInfos = appInfos.filter {
                it.`package` == "com.xc.r3" &&
                        it.compatibleModels.contains(selectedModel) &&
                        Build.VERSION.SDK_INT >= getApiLevelFromAndroidVersion(it.minAndroidVersion)
            }

            // Find the highest compatible AIR³ Manager version
            val highestCompatibleAir3ManagerInfo = compatibleAir3ManagerInfos.maxByOrNull { it.latestVersion }

            // Filter other apps based on compatibility
            appInfos = appInfos.filter { appInfo ->
                appInfo.`package` != "com.xc.r3" || compatibleAir3ManagerInfos.contains(appInfo)
            }

            val filteredAppInfos = appInfos.toMutableList()

            // Remove all existing AIR³ Manager entries
            filteredAppInfos.removeAll { it.`package` == "com.xc.r3" }

            // Add the highest compatible AIR³ Manager version if it's compatible with the Android version
            highestCompatibleAir3ManagerInfo?.let {
                filteredAppInfos.add(0, it)
            }

            // Update installedVersion and highestServerVersion for each AppInfo
            filteredAppInfos.onEach { appInfo ->
                appInfo.installedVersion = AppUtils.getAppVersion(context, appInfo.`package`)
                appInfo.highestServerVersion = appInfo.latestVersion
                Log.d("VersionChecker", "Setting highestServerVersion for ${appInfo.`package`} to ${appInfo.highestServerVersion}")
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
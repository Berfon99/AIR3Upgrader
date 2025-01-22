package com.example.air3upgrader

import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class VersionChecker {

    suspend fun getLatestVersionFromServer(selectedModel: String): List<AppInfo> = withContext(Dispatchers.IO) {
        try {
            val jsonString = downloadJson("https://ftp.fly-air3.com/Latest_Software_Download/versions.json")
            val appInfos = parseJson(jsonString)
            filterAppInfo(appInfos, selectedModel)
        } catch (e: Exception) {
            Log.e("VersionChecker", "Error getting latest version from server", e)
            emptyList()
        }
    }

    private fun filterAppInfo(appInfos: List<AppInfo>, selectedModel: String): List<AppInfo> {
        return appInfos.map { appInfo ->
            if (appInfo.name == "com.xc.r3") {
                val isModelCompatible = appInfo.compatibleModels.contains(selectedModel)
                val isAndroidVersionCompatible = Build.VERSION.SDK_INT >= appInfo.minAndroidVersion.toInt()
                if (isModelCompatible && isAndroidVersionCompatible) {
                    appInfo
                } else {
                    null
                }
            } else {
                appInfo
            }
        }.filterNotNull()
    }

    private fun parseJson(jsonString: String): List<AppInfo> {
        val gson = Gson()
        val listType = object : TypeToken<AppsData>() {}.type
        val appsData: AppsData = gson.fromJson(jsonString, listType)
        return appsData.apps
    }

    private fun downloadJson(urlString: String): String {
        var result = ""
        var urlConnection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.connect()

            val inputStream = urlConnection.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream))
            val stringBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                stringBuilder.append(line)
            }
            result = stringBuilder.toString()
        } catch (e: Exception) {
            Log.e("VersionChecker", "Error downloading JSON", e)
        } finally {
            urlConnection?.disconnect()
        }
        return result
    }
}
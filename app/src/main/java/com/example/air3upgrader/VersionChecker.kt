package com.example.air3upgrader

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class VersionChecker {

    data class AppInfo(
        val name: String,
        val packageName: String,
        val latestVersion: String,
        val apkPath: String,
        val compatibleModels: List<String>,
        val minAndroidVersion: String
    )

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
            if (appInfo.packageName == "com.xc.r3") {
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
        val appInfos = mutableListOf<AppInfo>()
        try {
            val jsonObject = JSONObject(jsonString)
            val appsArray: JSONArray = jsonObject.getJSONArray("apps")

            for (i in 0 until appsArray.length()) {
                val appObject: JSONObject = appsArray.getJSONObject(i)
                val name = appObject.getString("name")
                val packageName = appObject.getString("package")
                val latestVersion = appObject.getString("latestVersion")
                val apkPath = appObject.getString("apkPath")
                val compatibleModels = appObject.getJSONArray("compatibleModels").let { array ->
                    List(array.length()) { array.getString(it) }
                }
                val minAndroidVersion = appObject.getString("minAndroidVersion")

                val appInfo = AppInfo(name, packageName, latestVersion, apkPath, compatibleModels, minAndroidVersion)
                appInfos.add(appInfo)
            }
        } catch (e: Exception) {
            Log.e("VersionChecker", "Error parsing JSON", e)
        }
        return appInfos
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

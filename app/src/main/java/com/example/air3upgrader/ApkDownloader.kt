package com.example.air3upgrader

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

object ApkDownloader {
    fun downloadApk(client: OkHttpClient, appInfo: AppInfo, apkFile: File) {
        val baseUrl = "https://ftp.fly-air3.com" // Add the base URL
        val apkUrl = if (appInfo.apkPath.startsWith("http")) {
            appInfo.apkPath
        } else {
            baseUrl + appInfo.apkPath
        }
        val request = Request.Builder()
            .url(apkUrl)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw Exception("Failed to download APK: ${response.code}")

                val body = response.body ?: throw Exception("Response body is null")
                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ApkDownloader", "Error downloading APK", e)
            throw e
        }
    }
}
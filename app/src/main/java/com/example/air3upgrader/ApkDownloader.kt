package com.example.air3upgrader

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

object ApkDownloader {
    fun downloadApk(client: OkHttpClient, appInfo: AppInfo, apkFile: File) {
        val baseUrl = "https://ftp.fly-air3.com"
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
                if (!response.isSuccessful) {
                    throw IOException("Failed to download APK: ${response.code}")
                }

                val body = response.body ?: throw IOException("Response body is null")
                body.byteStream().use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e("ApkDownloader", "Error downloading APK: ${e.message}", e)
            // Handle the exception, e.g., display an error message to the user
            throw e // Re-throw the exception to be handled by the caller
        }
    }
}
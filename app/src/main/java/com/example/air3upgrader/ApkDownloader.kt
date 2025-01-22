package com.example.air3upgrader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException

object ApkDownloader {

    suspend fun downloadApk(client: OkHttpClient, appInfo: AppInfo, apkFile: File, progressCallback: (Int) -> Unit) {
        withContext(Dispatchers.IO) {
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
                    val totalBytes = body.contentLength()
                    var bytesDownloaded = 0L

                    body.source().buffer().use { source ->
                        apkFile.sink().buffer().use { sink ->
                            while (true) {
                                val readBytes = source.read(sink.buffer, 8192)
                                if (readBytes == -1L) break
                                bytesDownloaded += readBytes
                                val progress = (bytesDownloaded * 100 / totalBytes).toInt()
                                withContext(Dispatchers.Main) {
                                    progressCallback(progress)
                                }
                            }
                            sink.flush()
                        }
                    }
                }
            } catch (e: IOException) {
                Log.e("ApkDownloader", "Error downloading APK: ${e.message}", e)
                throw e
            }
        }
    }
}
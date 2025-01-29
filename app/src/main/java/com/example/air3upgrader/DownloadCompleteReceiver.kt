package com.example.air3upgrader // Replace with your package name

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File
import android.util.Log

class DownloadCompleteReceiver : BroadcastReceiver() {
    init {
        Log.d("DownloadReceiver", "DownloadCompleteReceiver instantiated")
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("DownloadReceiver", "onReceive() called")
        Log.d("DownloadReceiver", "Before if statement")
        Log.d("DownloadReceiver", "Intent action: ${intent.action}")
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            Log.d("DownloadReceiver", "Inside if statement")
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            Log.d("DownloadReceiver", "Download ID: $downloadId")

            if (downloadId != -1L) {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val statusColumnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusColumnIndex != -1) {
                        val status = cursor.getInt(statusColumnIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            // Get the downloaded APK file path
                            val filePath = getDownloadedApkFilePath(context, downloadId)
                            Log.d("DownloadReceiver", "File Path: $filePath")
                            val file = File(filePath)
                            Log.d("DownloadReceiver", "File exists: ${file.exists()}")
                            // Start the installation intent in a separate thread
                            Thread {
                                try {
                                    val contentUri = FileProvider.getUriForFile(
                                        context,
                                        context.packageName + ".provider", // Your FileProvider authority
                                        file
                                    )
                                    Log.d("DownloadReceiver", "Content URI: $contentUri")

                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(contentUri, "application/vnd.android.package-archive")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    }
                                    Log.d("DownloadReceiver", "Intent: $installIntent")

                                    context.startActivity(installIntent)
                                    // Display a Toast message indicating download completion
                                    Handler(Looper.getMainLooper()).post {
                                        Toast.makeText(context, "Download complete", Toast.LENGTH_SHORT).show()
                                    }
                                    (context as? MainActivity)?.downloadNextApp()
                                    (context as? MainActivity)?.getLatestVersionFromServer()
                                } catch (e: Exception) {
                                    Log.e("DownloadReceiver", "Error starting installation: ${e.message}", e)
                                }
                            }.start()
                        }
                    }
                }
                cursor.close()
            }
        }
    }

    // Helper function to get the downloaded APK file path
    private fun getDownloadedApkFilePath(context: Context, downloadId: Long): String {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        var result = ""
        if (cursor.moveToFirst()) {
            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (uriIndex != -1) {
                val uriString = cursor.getString(uriIndex)
                cursor.close()
                result = uriString.substringAfter("file://")
                Log.d("DownloadReceiver", "getDownloadedApkFilePath: $result")
                return result
            }
        }
        cursor.close()
        Log.d("DownloadReceiver", "getDownloadedApkFilePath: $result")
        return result
    }
}
package com.example.air3upgrader // Replace with your package name

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                // Get the downloaded APK file path
                val filePath = getDownloadedApkFilePath(context, downloadId)

                // Start the installation intent in a separate thread
                Thread {
                    val contentUri = FileProvider.getUriForFile(
                        context,
                        context.packageName + ".provider", // Your FileProvider authority
                        File(filePath)
                    )

                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(contentUri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }

                    context.startActivity(installIntent)
                }.start()
            }
        }
    }

    // Helper function to get the downloaded APK file path
    private fun getDownloadedApkFilePath(context: Context, downloadId: Long): String {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        if (cursor.moveToFirst()) {
            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (uriIndex != -1) {
                val uriString = cursor.getString(uriIndex)
                cursor.close()
                return uriString.substringAfter("file://")
            }
        }
        cursor.close()
        return ""
    }
}
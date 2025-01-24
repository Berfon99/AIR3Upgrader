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
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    if (uriIndex != -1) {
                        val uriString = cursor.getString(uriIndex)
                        // Convert the content URI to a file URI
                        val fileUri = Uri.fromFile(File(uriString.substringAfter("file://")))

                        // Get the content URI using FileProvider
                        val contentUri = FileProvider.getUriForFile(
                            context,
                            context.packageName + ".fileprovider", // Replace with your FileProvider authority
                            File(fileUri.path!!)
                        )

                        // Start installation intent
                        val installIntent = Intent(Intent.ACTION_VIEW)
                        installIntent.setDataAndType(contentUri, "application/vnd.android.package-archive")
                        installIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.startActivity(installIntent)
                    }
                }
                cursor.close()
            }
        }
    }
}
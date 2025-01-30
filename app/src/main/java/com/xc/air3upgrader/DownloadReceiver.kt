package com.xc.air3upgrader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d("DownloadReceiver", "Before if statement")
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            Log.d("DownloadReceiver", "Download complete")
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId != -1L) {
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (columnIndex != -1) {
                        val status = cursor.getInt(columnIndex)
                        if (status == DownloadManager.STATUS_SUCCESSFUL) {
                            val downloadedApkFilePath = getDownloadedApkFilePath(context, downloadId)
                            Log.d("DownloadReceiver", "getDownloadedApkFilePath: $downloadedApkFilePath")
                            if (downloadedApkFilePath != null) {
                                Log.d("DownloadReceiver", "File Path: $downloadedApkFilePath")
                                val file = File(downloadedApkFilePath)
                                if (file.exists()) {
                                    val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                                    } else {
                                        Uri.fromFile(file)
                                    }
                                    val installIntent = Intent(Intent.ACTION_VIEW)
                                    installIntent.setDataAndType(uri, "application/vnd.android.package-archive")
                                    installIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                    context.startActivity(installIntent)
                                } else {
                                    Log.e("DownloadReceiver", "File does not exist: $downloadedApkFilePath")
                                }
                            } else {
                                Log.e("DownloadReceiver", "Downloaded APK file path is null")
                            }
                        } else {
                            Log.e("DownloadReceiver", "Download failed with status: $status")
                        }
                    } else {
                        Log.e("DownloadReceiver", "COLUMN_STATUS not found in cursor")
                    }
                } else {
                    Log.e("DownloadReceiver", "Cursor is empty")
                }
                cursor.close()
            }
        }
    }

    private fun getDownloadedApkFilePath(context: Context, downloadId: Long): String? {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        var filePath: String? = null
        if (cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            if (columnIndex != -1) {
                val uriString = cursor.getString(columnIndex)
                if (uriString != null) {
                    filePath = Uri.parse(uriString).path
                }
            }
        }
        cursor.close()
        return filePath
    }
}
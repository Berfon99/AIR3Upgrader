package com.xc.air3upgrader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

class DownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("Before if statement")
        if (intent.action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            Timber.d("Download complete")
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
                            val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                            if (uriIndex != -1) {
                                val localUri = cursor.getString(uriIndex)
                                Timber.d("Local URI: $localUri")
                                if (localUri != null) {
                                    val apkFile = File(Uri.parse(localUri).path)
                                    Timber.d("File path: ${apkFile.absolutePath}")
                                    if (apkFile.exists()) {
                                        val authority = "${context.packageName}.provider"
                                        val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
                                        val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(apkUri, "application/vnd.android.package-archive")
                                            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(installIntent)
                                    } else {
                                        Timber.e("File does not exist: ${apkFile.absolutePath}")
                                    }
                                } else {
                                    Timber.e("Local URI is null")
                                }
                            } else {
                                Timber.e("COLUMN_LOCAL_URI not found")
                            }
                        } else {
                            Timber.e("Download failed with status: $status")
                        }
                    } else {
                        Timber.e("COLUMN_STATUS not found in cursor")
                    }
                } else {
                    Timber.e("Cursor is empty")
                }
                cursor.close()
            }
        }
    }
}
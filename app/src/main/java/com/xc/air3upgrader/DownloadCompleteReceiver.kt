package com.xc.air3upgrader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File
import java.util.LinkedList

class DownloadCompleteReceiver : BroadcastReceiver() {
    internal val downloadQueue = LinkedList<AppInfo>()
    companion object {
        private lateinit var instance: DownloadCompleteReceiver
        fun getInstance(context: Context): DownloadCompleteReceiver {
            if (!::instance.isInitialized) {
                instance = DownloadCompleteReceiver()
            }
            return instance
        }
    }
    private val downloadIdToAppInfo = mutableMapOf<Long, AppInfo>()
    private var fileName: String = ""
    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("DownloadCompleteReceiver: onReceive called")
        val action = intent.action
        if (action == null) {
            return
        }
        if (action == DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
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
                                if (!localUri.isNullOrEmpty()) {
                                    val apkFile = File(Uri.parse(localUri).path ?: "")
                                    Timber.d("File path: ${apkFile.absolutePath}")
                                    if (apkFile.exists()) {
                                        // Check if it's AIR³ Manager and rename the file if needed
                                        val appInfo = downloadIdToAppInfo.entries.firstOrNull { it.key == downloadId }?.value
                                        if (appInfo != null && appInfo.`package` == "com.xc.r3") {
                                            val originalFileName = appInfo.air3ManagerOriginalFileName
                                            if (originalFileName != null) {
                                                val newFile = File(apkFile.parent, originalFileName)
                                                if (apkFile.renameTo(newFile)) {
                                                    Timber.d("File renamed to: $originalFileName")
                                                    // Use the newFile for the install intent
                                                    val authority = "${context.packageName}.provider"
                                                    val apkUri: Uri = FileProvider.getUriForFile(context, authority, newFile)
                                                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(apkUri, "application/vnd.android.package-archive")
                                                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                                    }
                                                    context.startActivity(installIntent)
                                                    // Hide the progress bar after starting the install intent
                                                    MainActivity.getInstance()?.hideProgressBar()
                                                } else {
                                                    Timber.e("Failed to rename file to: $originalFileName")
                                                }
                                            }
                                        } else {
                                            // It's not AIR³ Manager, proceed with the original file
                                            try {
                                                val authority = "${context.packageName}.provider"
                                                val apkUri: Uri = FileProvider.getUriForFile(context, authority, apkFile)
                                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                                                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(installIntent)
                                                // Hide the progress bar after starting the install intent
                                                MainActivity.getInstance()?.hideProgressBar()
                                            } catch (e: IllegalArgumentException) {
                                                Timber.e("Failed to get URI for file: ${apkFile.absolutePath}. Error: ${e.message}")
                                            }
                                        }
                                    } else {
                                        Timber.e("APK file does not exist at: ${apkFile.absolutePath}")
                                    }
                                } else {
                                    Timber.e("Local URI is null or empty!")
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
                getInstance(context).downloadNextApp(context)
            }
        } else if (action == Intent.ACTION_PACKAGE_ADDED) {
            Timber.d("Package installed")
            val installedPackageName = intent.data?.schemeSpecificPart
            Timber.d("Installed package: $installedPackageName")
            // Trigger the refresh action
            MainActivity.getInstance()?.continueSetup()
        }
    }
    private fun enqueueDownloadAndInstallApk(context: Context, appInfo: AppInfo) {
        Timber.d("enqueueDownloadAndInstallApk() called for ${appInfo.name} with apkPath: ${appInfo.apkPath}")
        // Store the original filename for AIR³ Manager
        if (appInfo.`package` == "com.xc.r3") { // Check if it's AIR³ Manager
            appInfo.air3ManagerOriginalFileName = appInfo.apkPath.substringAfterLast("/")
            Timber.d("Storing original filename for AIR³ Manager: ${appInfo.air3ManagerOriginalFileName}")
        }
        val url = if (appInfo.apkPath.startsWith("http")) {
            appInfo.apkPath
        } else {
            "https://ftp.fly-air3.com${appInfo.apkPath}" // Construct the full URL here
        }
        // Use the original filename if it's AIR³ Manager, otherwise use the logic we had before
        fileName = if (appInfo.`package` == "com.xc.r3") {
            appInfo.air3ManagerOriginalFileName ?: "" // Use the original filename for AIR³ Manager
        } else {
            when {
                appInfo.name == "AIR³ Manager" -> {
                    "AIR3Manager.apk" // Use a shorter name for AIR³ Manager
                }
                appInfo.`package` == "indysoft.xc_guide" && !appInfo.apkPath.startsWith("/") -> {
                    "${appInfo.name}.apk" // Use the app name (e.g., "XCGuide-608.apk") for XC Guide from pg-race.aero
                }
                else -> {
                    appInfo.apkPath.substringAfterLast('/') // Use the original name for other APKs and XC Guide from ftp.fly-air3.com
                }
            }
        }
        Timber.d("Downloading from URL: $url, saving as: $fileName")

        val request = DownloadManager.Request(Uri.parse(url))
            .setDescription(appInfo.`package`) // Set the description to the package name
            .setTitle(appInfo.name) // Set the title to the app name
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)// Allow download over metered connections
            .setAllowedOverRoaming(true)// Allow download over roaming connections
            .setDestinationInExternalFilesDir(context, null, fileName) // Save to app's private directory
        Timber.d("Request: $request")
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        try {
            val downloadId = downloadManager.enqueue(request)
            downloadIdToAppInfo[downloadId] = appInfo
            Timber.d("Download enqueued")
        } catch (e: Exception) {
            Log.e("DownloadCompleteReceiver", "Error enqueuing download", e)
        }
    }
    internal fun downloadNextApp(context: Context) {
        Log.d("DownloadCompleteReceiver", "downloadNextApp() called")
        if (downloadQueue.isNotEmpty()) {
            val nextApp = downloadQueue.first
            downloadQueue.removeFirst()
            enqueueDownloadAndInstallApk(context, nextApp)
        } else {
            Log.d("DownloadCompleteReceiver", "downloadQueue is empty")
        }
    }
    fun enqueueDownload(appInfo: AppInfo) { // MODIFY THIS METHOD
        Timber.d("enqueueDownload() called for ${appInfo.name} with apkPath: ${appInfo.apkPath}")
        downloadQueue.add(appInfo)
    }
}
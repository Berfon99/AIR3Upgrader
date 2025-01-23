package com.example.air3upgrader

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import timber.log.Timber

object AppUtils {

    fun getAppVersion(context: Context, packageName: String): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val fullVersionName = packageInfo.versionName

            if (fullVersionName != null) {
                when (packageName) {
                    "org.xcontest.XCTrack" -> {
                        // Split by . and - and take the first 5 parts
                        val parts = fullVersionName.split(".", "-").take(5)
                        // Reconstruct the version string with the original separators
                        val reconstructedVersion = buildString {
                            var partIndex = 0
                            var charIndex = 0
                            while (partIndex < parts.size && charIndex < fullVersionName.length) {
                                val currentPart = parts[partIndex]
                                append(currentPart)
                                charIndex += currentPart.length
                                partIndex++
                                if (partIndex < parts.size) {
                                    while (charIndex < fullVersionName.length && fullVersionName[charIndex] != '.' && fullVersionName[charIndex] != '-') {
                                        charIndex++
                                    }
                                    if (charIndex < fullVersionName.length) {
                                        append(fullVersionName[charIndex])
                                        charIndex++
                                    }
                                }
                            }
                        }
                        reconstructedVersion
                    }
                    "indysoft.xc_guide" -> {
                        // Remove everything before the last dot
                        fullVersionName.substringAfterLast(".")
                    }
                    "com.xc.r3" -> {
                        // Extract major and minor version for AIR³ Manager using regex
                        val matchResult = Regex("(\\d+)\\.(\\d+)").find(fullVersionName)
                        matchResult?.let {
                            "${it.groupValues[1]}.${it.groupValues[2]}"
                        } ?: fullVersionName // Return full version if regex fails
                    }
                    else -> fullVersionName // Return the full version for other apps
                }
            } else {
                context.getString(R.string.na) // Use string resource
            }
        } catch (e: PackageManager.NameNotFoundException) {
            context.getString(R.string.na) // Use string resource
        }
    }

    fun setAppBackgroundColor(context: Context, packageName: String, nameTextView: TextView, installedVersion: String?, selectedModel: String?) {
        val serverVersion = getServerVersion(context, packageName, selectedModel) // Pass context here
        if (serverVersion != null && installedVersion != null) {
            if (VersionComparator.isServerVersionHigher(installedVersion, serverVersion, packageName)) {
                nameTextView.setBackgroundColor(ContextCompat.getColor(context, R.color.update_available))
            } else {
                nameTextView.setBackgroundColor(Color.TRANSPARENT)
            }
        } else {
            nameTextView.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    private fun getServerVersion(context: Context, packageName: String, selectedModel: String?): String? { // Add context parameter
        var result: String? = null
        runBlocking {
            result = withContext(Dispatchers.IO) {
                val appInfos = VersionChecker(context).getLatestVersionFromServer(selectedModel ?: "") // Pass context to VersionChecker
                appInfos.find { it.`package` == packageName }?.latestVersion
            }
        }
        return result
    }

    fun extractApkName(apkPath: String): String {
        val fileName = apkPath.substringAfterLast('/')
        return fileName.substringBeforeLast('.') // Remove the file extension
    }

    fun verifyApkIntegrity(apkFile: File, expectedSignature: String): Boolean {
        try {
            val messageDigest = MessageDigest.getInstance("SHA-256")
            val fileInputStream = FileInputStream(apkFile)
            val buffer = ByteArray(8192)
            var bytesRead: Int

            while (fileInputStream.read(buffer).also { bytesRead = it } > 0) {
                messageDigest.update(buffer, 0, bytesRead)
            }

            val calculatedSignature = messageDigest.digest().fold("") { str, it -> str + "%02x".format(it) }

            return calculatedSignature == expectedSignature
        } catch (e: Exception) {
            Timber.e(e, "Error verifying APK integrity")
            return false
        }
    }
}
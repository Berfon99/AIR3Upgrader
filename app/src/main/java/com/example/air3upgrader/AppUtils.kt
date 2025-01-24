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

    lateinit var appInfos: List<AppInfo>

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

    suspend fun setAppBackgroundColor(context: Context, packageName: String, nameTextView: TextView, installedVersion: String?, selectedModel: String?) {
        withContext(Dispatchers.Main) {
            val appInfo = appInfos.find { it.`package` == packageName } // Use appInfos directly
            val backgroundResource = when {
                VersionComparator.isServerVersionHigher(installedVersion ?: "", appInfo?.latestVersion ?: "", packageName) -> R.drawable.circle_background_orange
                installedVersion == appInfo?.latestVersion && !(appInfo?.isSelectedForUpgrade ?: false) -> R.drawable.circle_background_green
                appInfo?.isSelectedForUpgrade == true -> R.color.gray
                else -> R.drawable.circle_background
            }
            nameTextView.background = context.getDrawable(backgroundResource)
        }
    }

    fun getServerVersion(context: Context, packageName: String, selectedModel: String?): String? {
        var result: String? = null
        runBlocking {
            result = withContext(Dispatchers.IO) {
                val appInfos = VersionChecker(context).getLatestVersionFromServer(selectedModel ?: "")

                // Journalisation des informations de version récupérées depuis le serveur
                Timber.d("AppUtils - getServerVersion: appInfos = $appInfos")

                val appInfo = appInfos.find { it.`package` == packageName }

                // Journalisation de l'AppInfo trouvée pour le package spécifié
                Timber.d("AppUtils - getServerVersion: appInfo for $packageName = $appInfo")

                appInfo?.latestVersion
            }
        }

        // Journalisation de la version du serveur retournée
        Timber.d("AppUtils - getServerVersion: serverVersion for $packageName = $result")

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
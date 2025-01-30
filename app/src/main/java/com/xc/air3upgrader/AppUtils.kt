package com.xc.air3upgrader

import android.content.Context
import android.content.pm.PackageManager
import timber.log.Timber

object AppUtils {

    fun getAppVersion(context: Context, packageName: String): String {
        Timber.d("getAppVersion() called for package: $packageName")
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName ?: context.getString(R.string.na)
            Timber.d("Raw version name for $packageName: $versionName")
            val filteredVersion = filterVersion(versionName, packageName)
            Timber.d("Filtered version for $packageName: $filteredVersion")
            filteredVersion
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e, "Package not found: $packageName")
            context.getString(R.string.na)
        } catch (e: Exception) {
            Timber.e(e, "Error getting version for $packageName")
            context.getString(R.string.na)
        }
    }

    private fun filterVersion(versionName: String, packageName: String): String {
        Timber.d("filterVersion() called for package: $packageName, versionName: $versionName")
        return when (packageName) {
            "org.xcontest.XCTrack" -> {
                val parts = versionName.replace("-", ".").split(".") // Replace "-" with "." and split
                Timber.d("Parts after replace and split: $parts")
                val filteredParts = parts.take(5) // Take up to 5 parts
                Timber.d("Parts after take: $filteredParts")
                val paddedParts = filteredParts + List(5 - filteredParts.size) { "0" } // Pad with "0" if needed
                Timber.d("Parts after padding: $paddedParts")
                paddedParts.joinToString(".") // Join with "."
            }
            "indysoft.xc_guide" -> {
                val parts = versionName.split(".")
                Timber.d("Parts after split: $parts")
                if (parts.size > 1) {
                    val version = parts[1]
                    Timber.d("Version after filtering: $version")
                    version
                } else {
                    Timber.d("No filtering needed, returning: $versionName")
                    versionName
                }
            }
            "com.xc.r3" -> {
                val parts = versionName.split(".")
                Timber.d("Parts after split: $parts")
                if (parts.size > 1) {
                    val version = parts[0] + "." + parts[1]
                    Timber.d("Version after filtering: $version")
                    version
                } else {
                    Timber.d("No filtering needed, returning: $versionName")
                    versionName
                }
            }
            else -> {
                Timber.d("No filtering needed, returning: $versionName")
                versionName
            }
        }
    }
}
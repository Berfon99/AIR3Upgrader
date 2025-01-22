package com.example.air3upgrader

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

object AppManager {
    fun getAppName(context: Context, packageName: String): String {
        val packageManager = context.packageManager
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // Handle the case where the app is not found
            when (packageName) {
                "org.xcontest.XCTrack" -> "XCTrack"
                "indysoft.xc_guide" -> "XC Guide"
                "com.xc.r3" -> context.getString(R.string.app_name) // Use string resource
                else -> context.getString(R.string.unknown_app) // Use string resource
            }
        }
    }

    fun getAppVersionName(context: Context, packageName: String): String? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("AppManager", "Error getting app version", e)
            null
        }
    }

    fun getAppVersionCode(context: Context, packageName: String): Long? {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                packageInfo.versionCode.toLong()
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("AppManager", "Error getting app version", e)
            null
        }
    }

    fun parseXCTrackVersion(version: String?): String? {
        return version?.removePrefix("v")?.split("-")?.take(2)?.joinToString("-")
    }

    fun parseXCGuideVersion(version: String?): String? {
        return version?.removePrefix("v")?.substringAfter("1.", "")
    }
}
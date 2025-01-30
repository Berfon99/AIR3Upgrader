package com.xc.air3upgrader

data class AppInfo(
    var name: String, // Changed from val to var
    val `package`: String,
    var latestVersion: String,
    var apkPath: String, // Changed from val to var
    var installedVersion: String? = null,
    val compatibleModels: List<String>,
    val minAndroidVersion: String,
    var isSelectedForUpgrade: Boolean = false,
    var highestServerVersion: String = ""
) {
    override fun toString(): String {
        return "AppInfo(name='$name', package='$`package`', latestVersion='$latestVersion', apkPath='$apkPath', compatibleModels=$compatibleModels, minAndroidVersion='$minAndroidVersion', highestServerVersion='$highestServerVersion')"
    }
}

data class AppsData(
    val apps: List<AppInfo>
)
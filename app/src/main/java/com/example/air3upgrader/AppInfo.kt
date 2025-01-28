package com.example.air3upgrader

data class AppInfo(
    val name: String,
    val `package`: String,
    var latestVersion: String,
    val apkPath: String,
    var installedVersion: String? = null,
    val compatibleModels: List<String>,
    val minAndroidVersion: String,
    var isSelectedForUpgrade: Boolean = false, // Add this property
    var highestServerVersion: String = "" // Add this property
) {
    override fun toString(): String {
        return "AppInfo(name='$name', package='$`package`', latestVersion='$latestVersion', apkPath='$apkPath', compatibleModels=$compatibleModels, minAndroidVersion='$minAndroidVersion', highestServerVersion='$highestServerVersion')"
    }
}

data class AppsData(
    val apps: List<AppInfo>
)
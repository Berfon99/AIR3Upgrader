package com.example.air3upgrader

data class AppInfo(
    val name: String,
    val `package`: String,
    val latestVersion: String,
    val apkPath: String,
    val compatibleModels: List<String>,
    val minAndroidVersion: String
) {
    override fun toString(): String {
        return "AppInfo(name='$name', package='$`package`', latestVersion='$latestVersion', apkPath='$apkPath', compatibleModels=$compatibleModels, minAndroidVersion='$minAndroidVersion')"
    }
}

data class AppsData(
    val apps: List<AppInfo>
)
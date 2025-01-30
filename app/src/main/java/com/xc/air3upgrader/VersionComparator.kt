package com.xc.air3upgrader

import timber.log.Timber

object VersionComparator {
    fun isServerVersionHigher(installedVersion: String, serverVersion: String, packageName: String): Boolean {
        Timber.d("isServerVersionHigher() called for package: $packageName")
        Timber.d("  Installed Version: $installedVersion")
        Timber.d("  Server Version: $serverVersion")
        if (installedVersion == "N/A" || installedVersion == "not installed") {
            Timber.d("  Installed version is N/A or not installed, server version is higher")
            return true
        }

        val installedParts = if (packageName == "org.xcontest.XCTrack") {
            parseXCTrackVersion(installedVersion)
        } else {
            installedVersion.split(".").map { it.toIntOrNull() ?: 0 }
        }
        Timber.d("  Installed parts: $installedParts")

        val serverParts = if (packageName == "org.xcontest.XCTrack") {
            parseXCTrackVersion(serverVersion)
        } else {
            serverVersion.split(".").map { it.toIntOrNull() ?: 0 }
        }
        Timber.d("  Server parts: $serverParts")

        val maxParts = maxOf(installedParts.size, serverParts.size)
        for (i in 0 until maxParts) {
            val installedPart = installedParts.getOrElse(i) { 0 }
            val serverPart = serverParts.getOrElse(i) { 0 }
            if (serverPart > installedPart) {
                Timber.d("  Server version is higher at part $i")
                return true
            } else if (serverPart < installedPart) {
                Timber.d("  Server version is lower at part $i")
                return false
            }
            Timber.d("  Server version is equal at part $i")
        }
        Timber.d("  Versions are equal")
        return false
    }

    private fun parseXCTrackVersion(version: String): List<Int> {
        Timber.d("Parsing XCTrack version: $version")
        val filteredVersion = version.replace("-", ".")
        Timber.d("Filtered version: $filteredVersion")
        val parts = filteredVersion.split(".")
        Timber.d("Parts after split: $parts")
        val intParts = parts.mapNotNull { it.toIntOrNull() }
        Timber.d("Integer parts: $intParts")
        val paddedParts = intParts.toMutableList()
        while (paddedParts.size < 5) {
            paddedParts.add(0)
        }
        Timber.d("Padded parts: $paddedParts")
        val finalParts = paddedParts.take(5)
        Timber.d("Final parts: $finalParts")
        return finalParts
    }
}
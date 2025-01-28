package com.example.air3upgrader

import android.util.Log

object VersionComparator {
    fun isServerVersionHigher(installedVersion: String, serverVersion: String, packageName: String): Boolean {
        Log.d("VersionComparator", "isServerVersionHigher() called for package: $packageName")
        Log.d("VersionComparator", "  Installed Version: $installedVersion")
        Log.d("VersionComparator", "  Server Version: $serverVersion")
        if (installedVersion == "N/A" || installedVersion == "not installed") {
            Log.d("VersionComparator", "  Installed version is N/A or not installed, server version is higher")
            return true
        }

        val installedParts = if (packageName == "org.xcontest.XCTrack") {
            parseXCTrackVersion(installedVersion)
        } else {
            installedVersion.split(".").map { it.toIntOrNull() ?: 0 }
        }
        Log.d("VersionComparator", "  Installed parts: $installedParts")

        val serverParts = if (packageName == "org.xcontest.XCTrack") {
            parseXCTrackVersion(serverVersion)
        } else {
            serverVersion.split(".").map { it.toIntOrNull() ?: 0 }
        }
        Log.d("VersionComparator", "  Server parts: $serverParts")

        val maxParts = maxOf(installedParts.size, serverParts.size)
        for (i in 0 until maxParts) {
            val installedPart = installedParts.getOrElse(i) { 0 }
            val serverPart = serverParts.getOrElse(i) { 0 }
            if (serverPart > installedPart) {
                Log.d("VersionComparator", "  Server version is higher at part $i")
                return true
            } else if (serverPart < installedPart) {
                Log.d("VersionComparator", "  Server version is lower at part $i")
                return false
            }
            Log.d("VersionComparator", "  Server version is equal at part $i")
        }
        Log.d("VersionComparator", "  Versions are equal")
        return false
    }

    private fun parseXCTrackVersion(version: String): List<Int> {
        Log.d("VersionComparator", "Parsing XCTrack version: $version")
        val filteredVersion = version.replace("-", ".")
        Log.d("VersionComparator", "Filtered version: $filteredVersion")
        val parts = filteredVersion.split(".")
        Log.d("VersionComparator", "Parts after split: $parts")
        val intParts = parts.mapNotNull { it.toIntOrNull() }
        Log.d("VersionComparator", "Integer parts: $intParts")
        val paddedParts = intParts.toMutableList()
        while (paddedParts.size < 5) {
            paddedParts.add(0)
        }
        Log.d("VersionComparator", "Padded parts: $paddedParts")
        val finalParts = paddedParts.take(5)
        Log.d("VersionComparator", "Final parts: $finalParts")
        return finalParts
    }
}
package com.example.air3upgrader

import android.util.Log

object VersionComparator {
    fun isServerVersionHigher(installedVersion: String, serverVersion: String, packageName: String): Boolean {
        Log.d("VersionComparator", "installedVersion: $installedVersion, serverVersion: $serverVersion, packageName: $packageName")

        if (installedVersion == "N/A" || serverVersion == "N/A") {
            Log.d("VersionComparator", "One of the versions is N/A, returning ${installedVersion == "N/A"}")
            return installedVersion == "N/A" // If installed is N/A, it's lower
        }

        val installedParts = splitVersionString(installedVersion, packageName)
        val serverParts = splitVersionString(serverVersion, packageName)

        Log.d("VersionComparator", "installedParts: $installedParts, serverParts: $serverParts")

        val maxParts = maxOf(installedParts.size, serverParts.size)

        for (i in 0 until maxParts) {
            val installedPart = installedParts.getOrElse(i) { "0" } // Default to 0 if part is missing
            val serverPart = serverParts.getOrElse(i) { "0" } // Default to 0 if part is missing

            Log.d("VersionComparator", "installedPart: $installedPart, serverPart: $serverPart")

            val comparisonResult = compareVersionParts(installedPart, serverPart)

            Log.d("VersionComparator", "comparisonResult: $comparisonResult")

            if (comparisonResult != 0) {
                Log.d("VersionComparator", "Comparison result is not 0, returning ${comparisonResult < 0}")
                return comparisonResult < 0 // Server is higher if comparison is negative
            }
        }

        Log.d("VersionComparator", "Versions are equal, returning false")
        return false // Versions are equal
    }

    private fun splitVersionString(version: String, packageName: String): List<String> {
        return when (packageName) {
            "org.xcontest.XCTrack" -> version.split(".", "-")
            "indysoft.xc_guide" -> listOf(version)
            "com.xc.r3" -> listOf(version) // AIR³ Manager: traiter comme un seul nombre décimal
            else -> version.split(".", "-")
        }
    }

    private fun compareVersionParts(installedPart: String, serverPart: String): Int {
        val installedNum = installedPart.toIntOrNull()
        val serverNum = serverPart.toIntOrNull()

        return if (installedNum != null && serverNum != null) {
            Log.d("VersionComparator", "Comparing numerically: $installedNum vs $serverNum")
            installedNum.compareTo(serverNum) // Compare numerically
        } else {
            // Compare lexicographically, but handle non-numeric parts
            val installedNumeric = installedPart.filter { it.isDigit() }
            val serverNumeric = serverPart.filter { it.isDigit() }

            val installedNumericInt = installedNumeric.toIntOrNull() ?: 0
            val serverNumericInt = serverNumeric.toIntOrNull() ?: 0

            Log.d("VersionComparator", "Comparing lexicographically with numeric extraction: $installedNumericInt vs $serverNumericInt")
            installedNumericInt.compareTo(serverNumericInt)
        }
    }
}

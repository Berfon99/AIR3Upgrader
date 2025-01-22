package com.example.air3upgrader

object VersionComparator {
    fun isServerVersionHigher(installedVersion: String, serverVersion: String, packageName: String): Boolean {
        if (installedVersion == "N/A" || serverVersion == "N/A") {
            return installedVersion == "N/A" // If installed is N/A, it's lower
        }

        val installedParts = splitVersionString(installedVersion, packageName)
        val serverParts = splitVersionString(serverVersion, packageName)

        val maxParts = maxOf(installedParts.size, serverParts.size)

        for (i in 0 until maxParts) {
            val installedPart = installedParts.getOrElse(i) { "0" } // Default to 0 if part is missing
            val serverPart = serverParts.getOrElse(i) { "0" } // Default to 0 if part is missing

            val comparisonResult = compareVersionParts(installedPart, serverPart)
            if (comparisonResult != 0) {
                return comparisonResult < 0 // Server is higher if comparison is negative
            }
        }

        return false // Versions are equal
    }

    private fun splitVersionString(version: String, packageName: String): List<String> {
        return when (packageName) {
            "org.xcontest.XCTrack" -> version.split(".", "-")
            "indysoft.xc_guide" -> listOf(version)
            "com.xc.r3" -> version.split(".")
            else -> version.split(".", "-")
        }
    }

    private fun compareVersionParts(installedPart: String, serverPart: String): Int {
        val installedNum = installedPart.toIntOrNull()
        val serverNum = serverPart.toIntOrNull()

        if (installedNum != null && serverNum != null) {
            return installedNum.compareTo(serverNum) // Compare numerically
        } else {
            return installedPart.compareTo(serverPart) // Compare lexicographically
        }
    }
}
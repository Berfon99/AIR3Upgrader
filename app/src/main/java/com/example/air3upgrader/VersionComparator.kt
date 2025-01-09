package com.example.air3upgrader

object VersionComparator {
    fun isServerVersionHigher(installedVersion: String, serverVersion: String, packageName: String): Boolean {
        if (installedVersion == "N/A" || serverVersion == "N/A") {
            return false
        }

        val installedParts = splitVersionString(installedVersion, packageName)
        val serverParts = splitVersionString(serverVersion, packageName)

        val maxParts = maxOf(installedParts.size, serverParts.size)

        for (i in 0 until maxParts) {
            val installedPart = installedParts.getOrElse(i) { "" }
            val serverPart = serverParts.getOrElse(i) { "" }

            val comparisonResult = compareVersionParts(installedPart, serverPart)
            if (comparisonResult != 0) {
                return comparisonResult > 0
            }
        }

        return false
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
            return serverNum.compareTo(installedNum)
        } else {
            return serverPart.compareTo(installedPart)
        }
    }
}
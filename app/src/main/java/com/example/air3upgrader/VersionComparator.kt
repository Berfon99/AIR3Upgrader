package com.example.air3upgrader

object VersionComparator {
    fun isServerVersionHigher(installedVersion: String?, serverVersion: String?, packageName: String): Boolean {
        if (installedVersion == null || serverVersion == null) {
            return false
        }

        val installedParts = when (packageName) {
            "org.xcontest.XCTrack" -> installedVersion.split(".", "-")
            "indysoft.xc_guide" -> listOf(installedVersion)
            else -> listOf(installedVersion)
        }
        val serverParts = serverVersion.split(".", "-")

        val maxLength = maxOf(installedParts.size, serverParts.size)

        for (i in 0 until maxLength) {
            val installedPart = installedParts.getOrElse(i) { "0" }
            val serverPart = serverParts.getOrElse(i) { "0" }

            val installedNum = installedPart.toIntOrNull() ?: 0
            val serverNum = serverPart.toIntOrNull() ?: 0

            if (serverNum > installedNum) {
                return true
            } else if (serverNum < installedNum) {
                return false
            }
            if (installedPart.contains("-") && !serverPart.contains("-")) {
                return false
            } else if (!installedPart.contains("-") && serverPart.contains("-")) {
                return true
            }
        }
        return false
    }
}
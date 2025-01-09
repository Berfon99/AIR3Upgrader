package com.example.air3upgrader

import android.util.Log

object VersionComparator {
    fun isServerVersionHigher(installedVersion: String, serverVersion: String, packageName: String): Boolean {
        if (installedVersion == "N/A" || serverVersion == "N/A") {
            return false
        }
        return try {
            val installedVersionParts = installedVersion.split(".").map { it.toInt() }
            val serverVersionParts = serverVersion.split(".").map { it.toInt() }

            val maxLength = maxOf(installedVersionParts.size, serverVersionParts.size)
            for (i in 0 until maxLength) {
                val installed = installedVersionParts.getOrElse(i) { 0 }
                val server = serverVersionParts.getOrElse(i) { 0 }
                if (server > installed) {
                    return true
                } else if (server < installed) {
                    return false
                }
            }
            false // Versions are equal
        } catch (e: NumberFormatException) {
            Log.e("VersionComparator", "Error parsing version for $packageName: $e")
            false // Assume not higher if parsing fails
        }
    }
}

package com.xc.air3upgrader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class XcGuideVersionChecker {

    suspend fun getXcGuideVersion(): String? = withContext(Dispatchers.IO) {
        val versionUrl = "https://pg-race.aero/xcguide/version.txt"
        try {
            val url = URL(versionUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream))
                val version = reader.readLine() // Read the single line
                reader.close()
                return@withContext version
            } else {
                println("Error: HTTP response code ${connection.responseCode}")
                return@withContext null
            }
        } catch (e: Exception) {
            println("Error fetching XC Guide version: ${e.message}")
            return@withContext null
        }
    }
}
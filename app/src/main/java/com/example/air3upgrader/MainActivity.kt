package com.example.air3upgrader

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var xctrackName: TextView
    private lateinit var xcguideName: TextView
    private lateinit var air3managerName: TextView
    private lateinit var closeButton: Button
    private lateinit var xctrackVersion: TextView
    private lateinit var xcguideVersion: TextView
    private lateinit var xctrackServerVersion: TextView
    private lateinit var xcguideServerVersion: TextView

    // Package names of the apps we want to check
    private val xctrackPackageName = "org.xcontest.XCTrack"
    private val xcguidePackageName = "indysoft.xc_guide"
    private val air3managerPackageName = "com.xc.r3"

    private val versionChecker = VersionChecker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize TextViews
        xctrackName = findViewById(R.id.xctrack_name)
        xcguideName = findViewById(R.id.xcguide_name)
        air3managerName = findViewById(R.id.air3manager_name)
        closeButton = findViewById(R.id.close_button)
        xctrackVersion = findViewById(R.id.xctrack_version)
        xcguideVersion = findViewById(R.id.xcguide_version)
        xctrackServerVersion = findViewById(R.id.xctrack_server_version)
        xcguideServerVersion = findViewById(R.id.xcguide_server_version)

        // Check if the apps are installed and update the UI
        checkAppInstallation(xctrackPackageName, xctrackName, xctrackVersion)
        checkAppInstallation(xcguidePackageName, xcguideName, xcguideVersion)
        checkAppInstallation(air3managerPackageName, air3managerName, null)

        // Set onClick listener for the close button
        closeButton.setOnClickListener {
            finish() // Close the app
        }

        // Get the latest version from the server
        getLatestVersionFromServer()
    }

    private fun checkAppInstallation(packageName: String, nameTextView: TextView, versionTextView: TextView?) {
        val packageManager: PackageManager = this.packageManager
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            if (packageInfo != null) {
                Log.i("AppCheck", "$packageName: getPackageInfo() returned a result")
                // App is installed
                nameTextView.text = getAppName(packageName)
                // Get the version name
                var versionName: String? = packageInfo.versionName

                // Parse the version name
                versionName = when (packageName) {
                    xctrackPackageName -> parseXCTrackVersion(versionName)
                    xcguidePackageName -> parseXCGuideVersion(versionName)
                    else -> versionName
                }

                // Get the version code
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }

                // Display the version
                if (versionTextView != null) {
                    if (packageName == air3managerPackageName) {
                        versionTextView.text = "v$versionName ($versionCode)"
                    } else {
                        versionTextView.text = versionName ?: "N/A"
                    }
                }
                // Set the background color
                setAppBackgroundColor(packageName, nameTextView, versionName)
            } else {
                Log.i("AppCheck", "$packageName: getPackageInfo() returned null")
                // App is not installed
                nameTextView.text = getAppName(packageName)
                nameTextView.background = ContextCompat.getDrawable(this, R.drawable.circle_background_black)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("AppCheck", "$packageName: NameNotFoundException", e)
            // App is not installed
            nameTextView.text = getAppName(packageName)
            nameTextView.background = ContextCompat.getDrawable(this, R.drawable.circle_background_black)
        }
    }

    private fun getAppName(packageName: String): String {
        val packageManager = packageManager
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // Handle the case where the app is not found
            when (packageName) {
                xctrackPackageName -> "XCTrack"
                xcguidePackageName -> "XC Guide"
                air3managerPackageName -> "AIR³ Manager"
                else -> "Unknown App"
            }
        }
    }

    private fun getLatestVersionFromServer() {
        CoroutineScope(Dispatchers.IO).launch {
            val xctrackLatestVersion = versionChecker.getLatestVersion(xctrackPackageName)
            val xcguideLatestVersion = versionChecker.getLatestVersion(xcguidePackageName)

            withContext(Dispatchers.Main) {
                xctrackServerVersion.text = xctrackLatestVersion ?: "N/A"
                xcguideServerVersion.text = xcguideLatestVersion ?: "N/A"
                setAppBackgroundColor(xctrackPackageName, xctrackName, xctrackVersion.text.toString())
                setAppBackgroundColor(xcguidePackageName, xcguideName, xcguideVersion.text.toString())
            }
        }
    }

    private fun parseXCTrackVersion(version: String?): String? {
        return version?.removePrefix("v")?.split("-")?.take(2)?.joinToString("-")
    }

    private fun parseXCGuideVersion(version: String?): String? {
        return version?.removePrefix("v")?.substringAfter("1.", "")
    }

    private fun setAppBackgroundColor(packageName: String, nameTextView: TextView, installedVersion: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            val latestVersion = versionChecker.getLatestVersion(packageName)
            withContext(Dispatchers.Main) {
                if (latestVersion != null && installedVersion != null) {
                    if (packageName == air3managerPackageName) {
                        nameTextView.background = ContextCompat.getDrawable(nameTextView.context, R.drawable.circle_background_green)
                    } else if (latestVersion == installedVersion) {
                        nameTextView.background = ContextCompat.getDrawable(nameTextView.context, R.drawable.circle_background_green)
                    } else {
                        nameTextView.background = ContextCompat.getDrawable(nameTextView.context, R.drawable.circle_background_orange)
                    }
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Open the settings window
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
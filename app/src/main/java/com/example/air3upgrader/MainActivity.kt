package com.example.air3upgrader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var xctrackName: TextView
    private lateinit var xcguideName: TextView
    private lateinit var air3managerName: TextView
    private lateinit var closeButton: Button
    private lateinit var upgradeButton: Button
    private lateinit var xctrackVersion: TextView
    private lateinit var xcguideVersion: TextView
    private lateinit var xctrackServerVersion: TextView
    private lateinit var xcguideServerVersion: TextView
    private lateinit var xctrackCheckbox: CheckBox
    private lateinit var xcguideCheckbox: CheckBox

    // Package names of the apps we want to check
    private val xctrackPackageName = "org.xcontest.XCTrack"
    private val xcguidePackageName = "indysoft.xc_guide"
    private val air3managerPackageName = "com.xc.r3"

    private val versionChecker = VersionChecker()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize TextViews and Checkboxes
        xctrackName = findViewById(R.id.xctrack_name)
        xcguideName = findViewById(R.id.xcguide_name)
        air3managerName = findViewById(R.id.air3manager_name)
        closeButton = findViewById(R.id.close_button)
        upgradeButton = findViewById(R.id.upgrade_button)
        xctrackVersion = findViewById(R.id.xctrack_version)
        xcguideVersion = findViewById(R.id.xcguide_version)
        xctrackServerVersion = findViewById(R.id.xctrack_server_version)
        xcguideServerVersion = findViewById(R.id.xcguide_server_version)
        xctrackCheckbox = findViewById(R.id.xctrack_checkbox)
        xcguideCheckbox = findViewById(R.id.xcguide_checkbox)

        // Check if the apps are installed and update the UI
        checkAppInstallation(xctrackPackageName, xctrackName, xctrackVersion)
        checkAppInstallation(xcguidePackageName, xcguideName, xcguideVersion)
        checkAppInstallation(air3managerPackageName, air3managerName, null)

        // Set onClick listener for the close button
        closeButton.setOnClickListener {
            finish() // Close the app
        }

        // Set onClick listener for the upgrade button
        upgradeButton.setOnClickListener {
            // Handle upgrade button click
            handleUpgradeButtonClick()
        }

        // Get the latest version from the server
        getLatestVersionFromServer()
    }

    override fun onResume() {
        super.onResume()
        // Refresh the app information
        checkAppInstallation(xctrackPackageName, xctrackName, xctrackVersion)
        checkAppInstallation(xcguidePackageName, xcguideName, xcguideVersion)
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
                CoroutineScope(Dispatchers.Main).launch {
                    setAppBackgroundColor(packageName, nameTextView, versionName)
                }
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
                launch {
                    setAppBackgroundColor(
                        xctrackPackageName,
                        xctrackName,
                        xctrackVersion.text.toString()
                    )
                }
                launch {
                    setAppBackgroundColor(
                        xcguidePackageName,
                        xcguideName,
                        xcguideVersion.text.toString()
                    )
                }
                setCheckboxState(
                    xctrackPackageName,
                    xctrackCheckbox,
                    xctrackVersion.text.toString(),
                    xctrackLatestVersion
                )
                setCheckboxState(
                    xcguidePackageName,
                    xcguideCheckbox,
                    xcguideVersion.text.toString(),
                    xcguideLatestVersion
                )
            }
        }
    }

    private fun parseXCTrackVersion(version: String?): String? {
        return version?.removePrefix("v")?.split("-")?.take(2)?.joinToString("-")
    }

    private fun parseXCGuideVersion(version: String?): String? {
        return version?.removePrefix("v")?.substringAfter("1.", "")
    }

    private suspend fun setAppBackgroundColor(packageName: String, nameTextView: TextView, installedVersion: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            val latestVersion = versionChecker.getLatestVersion(packageName)
            withContext(Dispatchers.Main) {
                if (latestVersion != null && installedVersion != null) {
                    if (packageName == air3managerPackageName) {
                        nameTextView.background = ContextCompat.getDrawable(nameTextView.context, R.drawable.circle_background_green)
                    } else if (versionChecker.isServerVersionHigher(installedVersion, latestVersion, packageName)) {
                        nameTextView.background = ContextCompat.getDrawable(nameTextView.context, R.drawable.circle_background_orange)
                    } else {
                        nameTextView.background = ContextCompat.getDrawable(nameTextView.context, R.drawable.circle_background_green)
                    }
                }
            }
        }
    }

    private fun setCheckboxState(packageName: String, checkBox: CheckBox, installedVersion: String, serverVersion: String?) {
        if (serverVersion != null) {
            checkBox.isChecked = versionChecker.isServerVersionHigher(installedVersion, serverVersion, packageName)
        } else {
            checkBox.isChecked = false
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageManager.canRequestPackageInstalls()) {
                    Log.i("Permission", "Permission granted")
                } else {
                    Log.i("Permission", "Permission denied")
                }
            }
        }

    private fun handleUpgradeButtonClick() {
        val appsToUpgrade = mutableListOf<AppInfo>()

        CoroutineScope(Dispatchers.IO).launch {
            if (xctrackCheckbox.isChecked) {
                val xctrackInfo = versionChecker.getAppInfo(xctrackPackageName)
                if (xctrackInfo != null) {
                    appsToUpgrade.add(xctrackInfo)
                }
            }

            if (xcguideCheckbox.isChecked) {
                val xcguideInfo = versionChecker.getAppInfo(xcguidePackageName)
                if (xcguideInfo != null) {
                    appsToUpgrade.add(xcguideInfo)
                }
            }

            if (appsToUpgrade.isNotEmpty()) {
                for (appInfo in appsToUpgrade) {
                    downloadAndInstallApk(appInfo)
                }
            }
        }
    }


    private suspend fun downloadAndInstallApk(appInfo: AppInfo) {
        Log.i("MainActivity", "downloadAndInstallApk: $appInfo")
        val apkName = appInfo.name + ".apk"
        val apkFile = File(getExternalFilesDir(null), apkName)
        try {
            versionChecker.downloadApk(appInfo, apkFile)
            withContext(Dispatchers.Main) {
                if (checkInstallPermission()) {
                    installApk(apkFile)
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error downloading or installing APK", e)
        }
    }

    private fun installApk(apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val apkUri = androidx.core.content.FileProvider.getUriForFile(this, "${packageName}.provider", apkFile)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            apkUri
        } else {
            android.net.Uri.fromFile(apkFile)
        }
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        val packageName = getPackageNameFromApk(apkFile)
        if (packageName != null) {
            val newVersion = getAppVersion(packageName)
            if (packageName == xctrackPackageName) {
                xctrackVersion.text = newVersion
                CoroutineScope(Dispatchers.Main).launch {
                    setAppBackgroundColor(xctrackPackageName, xctrackName, xctrackVersion.text.toString())
                }
            } else if (packageName == xcguidePackageName) {
                xcguideVersion.text = newVersion
                CoroutineScope(Dispatchers.Main).launch {
                    setAppBackgroundColor(xcguidePackageName, xcguideName, xcguideVersion.text.toString())
                }
            }
        }
    }

    private fun getAppVersion(packageName: String): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "N/A" // Use the elvis operator to provide a default value
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("MainActivity", "Error getting app version", e)
            "N/A"
        }
    }

    private fun getPackageNameFromApk(apkFile: File): String? {
        return try {
            val packageManager = packageManager
            val packageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            packageInfo?.packageName
        } catch (e: Exception) {
            Log.e("MainActivity", "Error getting package name from APK", e)
            null
        }
    }

    private fun checkInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                showPermissionDialog()
                return false
            }
        }
        return true
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("To install apps, you need to allow this app to install unknown apps.")
            .setPositiveButton("Allow") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:$packageName")
                requestPermissionLauncher.launch(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
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
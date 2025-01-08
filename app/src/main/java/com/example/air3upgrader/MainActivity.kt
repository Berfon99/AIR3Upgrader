package com.example.air3upgrader

import android.content.Intent
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

        // Set the action bar title with device info
        setActionBarTitle()

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

    private fun setActionBarTitle() {
        val deviceModel = Build.MODEL
        val androidVersion = Build.VERSION.RELEASE
        supportActionBar?.title = "$deviceModel (Android $androidVersion)"
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Navigate to SettingsActivity
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_about -> {
                // Handle "About" action (e.g., show an about dialog)
                //Toast.makeText(this, "About", Toast.LENGTH_SHORT).show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh the app information
        checkAppInstallation(xctrackPackageName, xctrackName, xctrackVersion)
        checkAppInstallation(xcguidePackageName, xcguideName, xcguideVersion)
        getLatestVersionFromServer()
    }

    private fun checkAppInstallation(packageName: String, nameTextView: TextView, versionTextView: TextView?) {
        val versionName = AppManager.getAppVersionName(this, packageName)
        val versionCode = AppManager.getAppVersionCode(this, packageName)
        val parsedVersionName = when (packageName) {
            xctrackPackageName -> AppManager.parseXCTrackVersion(versionName)
            xcguidePackageName -> AppManager.parseXCGuideVersion(versionName)
            else -> versionName
        }
        UiUpdater.updateAppInfo(this, packageName, nameTextView, versionTextView, parsedVersionName, versionCode)
    }

    private fun getLatestVersionFromServer() {
        CoroutineScope(Dispatchers.IO).launch {
            val xctrackLatestVersion = versionChecker.getLatestVersion(xctrackPackageName)
            val xcguideLatestVersion = versionChecker.getLatestVersion(xcguidePackageName)

            withContext(Dispatchers.Main) {
                UiUpdater.updateServerVersion(xctrackServerVersion, xcguideServerVersion, xctrackLatestVersion, xcguideLatestVersion)
                launch {
                    AppUtils.setAppBackgroundColor(
                        this@MainActivity,
                        xctrackPackageName,
                        xctrackName,
                        xctrackVersion.text.toString() ?: "N/A"
                    )
                }
                launch {
                    AppUtils.setAppBackgroundColor(
                        this@MainActivity,
                        xcguidePackageName,
                        xcguideName,
                        xcguideVersion.text.toString() ?: "N/A"
                    )
                }
                UiUpdater.updateCheckboxState(
                    xctrackPackageName,
                    xctrackCheckbox,
                    xctrackVersion.text.toString(),
                    xctrackLatestVersion
                )
                UiUpdater.updateCheckboxState(
                    xcguidePackageName,
                    xcguideCheckbox,
                    xcguideVersion.text.toString(),
                    xcguideLatestVersion
                )
            }
        }
    }

    private fun setCheckboxState(packageName: String, checkBox: CheckBox, installedVersion: String, serverVersion: String?) {
        if (serverVersion != null) {
            checkBox.isChecked = VersionComparator.isServerVersionHigher(installedVersion, serverVersion, packageName)
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
        val packageName = AppUtils.getPackageNameFromApk(this, apkFile)
        if (packageName != null) {
            val newVersion = AppUtils.getAppVersion(this, packageName)
            if (packageName == xctrackPackageName) {
                xctrackVersion.text = newVersion
                CoroutineScope(Dispatchers.Main).launch {
                    AppUtils.setAppBackgroundColor(this@MainActivity, xctrackPackageName, xctrackName, xctrackVersion.text.toString())
                }
            } else if (packageName == xcguidePackageName) {
                xcguideVersion.text = newVersion
                CoroutineScope(Dispatchers.Main).launch {
                    AppUtils.setAppBackgroundColor(this@MainActivity, xcguidePackageName, xcguideName, xcguideVersion.text.toString())
                }
            }
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
            .setMessage("To install apps from unknown sources, you need to grant permission.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:$packageName")
                requestPermissionLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

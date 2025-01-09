package com.example.air3upgrader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log // Add this import statement
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var xctrackName: TextView
    private lateinit var xcguideName: TextView
    private lateinit var air3managerName: TextView
    private lateinit var closeButton: Button
    private lateinit var upgradeButton: Button
    private lateinit var xctrackVersion: TextView
    private lateinit var xcguideVersion: TextView
    private lateinit var air3managerVersion: TextView
    private lateinit var xctrackServerVersion: TextView
    private lateinit var xcguideServerVersion: TextView
    private lateinit var air3managerServerVersion: TextView
    private lateinit var xctrackCheckbox: CheckBox
    private lateinit var xcguideCheckbox: CheckBox
    private lateinit var air3managerCheckbox: CheckBox
    private lateinit var dataStoreManager: DataStoreManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var selectedModel: String = ""
    private var appInfos: List<VersionChecker.AppInfo> = emptyList()

    // Package names of the apps we want to check
    private val xctrackPackageName = "org.xcontest.XCTrack"
    private val xcguidePackageName = "indysoft.xc_guide"
    private val air3managerPackageName = "com.xc.r3"

    private val versionChecker = VersionChecker()
    private var downloadID: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dataStoreManager = DataStoreManager(this)

        // Set the action bar title with device info
        setActionBarTitleWithSelectedModel()

        // Initialize TextViews and Checkboxes
        xctrackName = findViewById(R.id.xctrack_name)
        xcguideName = findViewById(R.id.xcguide_name)
        air3managerName = findViewById(R.id.air3manager_name)
        closeButton = findViewById(R.id.close_button)
        upgradeButton = findViewById(R.id.upgrade_button)
        xctrackVersion = findViewById(R.id.xctrack_version)
        xcguideVersion = findViewById(R.id.xcguide_version)
        air3managerVersion = findViewById(R.id.air3manager_version)
        xctrackServerVersion = findViewById(R.id.xctrack_server_version)
        xcguideServerVersion = findViewById(R.id.xcguide_server_version)
        air3managerServerVersion = findViewById(R.id.air3manager_server_version)
        xctrackCheckbox = findViewById(R.id.xctrack_checkbox)
        xcguideCheckbox = findViewById(R.id.xcguide_checkbox)
        air3managerCheckbox = findViewById(R.id.air3manager_checkbox)

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

        // Keep the screen on
        acquireWakeLock()

        registerReceiver(onDownloadComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Handle settings item click
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_about -> {
                // Handle about item click
                val intent = Intent(this, AboutActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release the wake lock
        releaseWakeLock()
        unregisterReceiver(onDownloadComplete)
    }

    override fun onResume() {
        super.onResume()
        // Check if the apps are installed and update the UI
        checkAppInstallation(xctrackPackageName, xctrackName, xctrackVersion, selectedModel)
        checkAppInstallation(xcguidePackageName, xcguideName, xcguideVersion, selectedModel)
        checkAppInstallation(air3managerPackageName, air3managerName, air3managerVersion, selectedModel)
    }

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadID == id) {
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query()
                query.setFilterById(id)
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (DownloadManager.STATUS_SUCCESSFUL == cursor.getInt(columnIndex)) {
                        val uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                        val file = File(Uri.parse(uriString).path!!)
                        installApk(file)
                    }
                }
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AIR3Upgrader:KeepScreenOn"
        )
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun setActionBarTitleWithSelectedModel() {
        lifecycleScope.launch {
            dataStoreManager.getSelectedModel().collectLatest { selectedModel ->
                val currentSelectedModel = selectedModel ?: getDeviceName()
                val androidVersion = Build.VERSION.RELEASE // Get the Android version
                supportActionBar?.title = "$currentSelectedModel - Android $androidVersion" // Set the title correctly
            }
        }
    }

    private fun getDeviceName(): String {
        return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
    }

    private fun getLatestVersionFromServer() {
        lifecycleScope.launch {
            dataStoreManager.getSelectedModel().collectLatest { selectedModel ->
                this@MainActivity.selectedModel = selectedModel ?: getDeviceName()
                try {
                    appInfos = versionChecker.getLatestVersionFromServer(this@MainActivity.selectedModel)

                    // Update UI for XCTrack
                    val xctrackAppInfo = appInfos.find { it.packageName == xctrackPackageName }
                    xctrackAppInfo?.let {
                        UiUpdater.updateAppInfo(
                            context = this@MainActivity,
                            packageName = it.packageName,
                            nameTextView = xctrackName,
                            versionTextView = xctrackServerVersion,
                            versionName = it.latestVersion,
                            versionCode = null, // `AppInfo` does not provide versionCode
                            selectedModel = this@MainActivity.selectedModel // Pass selectedModel
                        )
                    }

                    // Update UI for XCGuide
                    val xcguideAppInfo = appInfos.find { it.packageName == xcguidePackageName }
                    xcguideAppInfo?.let {
                        UiUpdater.updateAppInfo(
                            context = this@MainActivity,
                            packageName = it.packageName,
                            nameTextView = xcguideName,
                            versionTextView = xcguideServerVersion,
                            versionName = it.latestVersion,
                            versionCode = null,
                            selectedModel = this@MainActivity.selectedModel // Pass selectedModel
                        )
                    }

                    // Update UI for AIR3Manager
                    val air3managerAppInfo = appInfos.find { it.packageName == air3managerPackageName }
                    air3managerAppInfo?.let {
                        UiUpdater.updateAppInfo(
                            context = this@MainActivity,
                            packageName = it.packageName,
                            nameTextView = air3managerName,
                            versionTextView = air3managerServerVersion,
                            versionName = it.latestVersion,
                            versionCode = null,
                            selectedModel = this@MainActivity.selectedModel // Pass selectedModel
                        )
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error getting latest version from server", e)
                }
            }
        }
    }

    private fun handleUpgradeButtonClick() {
        val appsToUpgrade = mutableListOf<VersionChecker.AppInfo>()
        if (xctrackCheckbox.isChecked) {
            appInfos.find { it.packageName == xctrackPackageName }?.let { appsToUpgrade.add(it) }
        }
        if (xcguideCheckbox.isChecked) {
            appInfos.find { it.packageName == xcguidePackageName }?.let { appsToUpgrade.add(it) }
        }
        if (air3managerCheckbox.isChecked) {
            appInfos.find { it.packageName == air3managerPackageName }?.let { appsToUpgrade.add(it) }
        }

        if (appsToUpgrade.isEmpty()) {
            Toast.makeText(this, "No apps selected for upgrade", Toast.LENGTH_SHORT).show()
            return
        }

        for (appInfo in appsToUpgrade) {
            val fullApkUrl = "https://ftp.fly-air3.com${appInfo.apkPath}"
            downloadAndInstallApk(fullApkUrl, appInfo.packageName)
        }
    }

    private fun downloadAndInstallApk(apkUrl: String, packageName: String) {
        if (!checkInstallPermission()) {
            showPermissionDialog()
            return
        }

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Downloading $packageName")
            .setDescription("Downloading the latest version of $packageName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "$packageName.apk")

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadID = downloadManager.enqueue(request)
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }

    private fun checkInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun showPermissionDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Install Permission Required")
            .setMessage("To install apps, you need to allow installation from unknown sources.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                permissionLauncher.launch(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (checkInstallPermission()) {
            Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAppInstallation(packageName: String, nameTextView: TextView, versionTextView: TextView?, selectedModel: String) {
        val installedVersion = AppUtils.getAppVersion(this, packageName)
        if (versionTextView != null) {
            versionTextView.text = if (installedVersion != "N/A") "Installed: $installedVersion" else "Not installed"
        }
        lifecycleScope.launch {
            try {
                AppUtils.setAppBackgroundColor(this@MainActivity, packageName, nameTextView, installedVersion, selectedModel)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

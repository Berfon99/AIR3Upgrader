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
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
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
    private lateinit var xctrackApkName: TextView
    private lateinit var xcguideApkName: TextView
    private lateinit var air3managerApkName: TextView

    private var wakeLock: PowerManager.WakeLock? = null
    private var selectedModel: String = ""
    private var appInfos: List<VersionChecker.AppInfo> = emptyList()
    private var downloadQueue: MutableList<VersionChecker.AppInfo> = mutableListOf()
    private var downloadIdToAppInfo: MutableMap<Long, VersionChecker.AppInfo> = mutableMapOf()

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
        xctrackApkName = findViewById(R.id.xctrack_apk_name)
        xcguideApkName = findViewById(R.id.xcguide_apk_name)
        air3managerApkName = findViewById(R.id.air3manager_apk_name)

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

        // XCTrack Checkbox Listener
        xctrackCheckbox.setOnCheckedChangeListener { _, isChecked ->
            xctrackApkName.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // XCGuide Checkbox Listener
        xcguideCheckbox.setOnCheckedChangeListener { _, isChecked ->
            xcguideApkName.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // AIR3Manager Checkbox Listener
        air3managerCheckbox.setOnCheckedChangeListener { _, isChecked ->
            air3managerApkName.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun extractApkName(apkPath: String): String {
        return apkPath.substringAfterLast("/")
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
        lifecycleScope.launch {
            val selectedModel: String? = dataStoreManager.getSelectedModel().firstOrNull()
            val finalSelectedModel = selectedModel ?: getDeviceName()
            AppUtils.setAppBackgroundColor(this@MainActivity, xctrackPackageName, xctrackName, AppUtils.getAppVersion(this@MainActivity, xctrackPackageName), finalSelectedModel)
            AppUtils.setAppBackgroundColor(this@MainActivity, xcguidePackageName, xcguideName, AppUtils.getAppVersion(this@MainActivity, xcguidePackageName), finalSelectedModel)
            AppUtils.setAppBackgroundColor(this@MainActivity, air3managerPackageName, air3managerName, AppUtils.getAppVersion(this@MainActivity, air3managerPackageName), finalSelectedModel)
        }
        // Update checkbox states after installation
        updateCheckboxStates()
        startNextDownload()
    }

    private fun updateCheckboxStates() {
        lifecycleScope.launch {
            val selectedModel: String? = dataStoreManager.getSelectedModel().firstOrNull()
            val finalSelectedModel = selectedModel ?: getDeviceName()
            appInfos.forEach { appInfo ->
                when (appInfo.packageName) {
                    xctrackPackageName -> {
                        val installedVersion = AppUtils.getAppVersion(this@MainActivity, xctrackPackageName)
                        UiUpdater.updateCheckboxState(xctrackPackageName, xctrackCheckbox, installedVersion, appInfo.latestVersion)
                    }
                    xcguidePackageName -> {
                        val installedVersion = AppUtils.getAppVersion(this@MainActivity, xcguidePackageName)
                        UiUpdater.updateCheckboxState(xcguidePackageName, xcguideCheckbox, installedVersion, appInfo.latestVersion)
                    }
                    air3managerPackageName -> {
                        val installedVersion = AppUtils.getAppVersion(this@MainActivity, air3managerPackageName)
                        UiUpdater.updateCheckboxState(air3managerPackageName, air3managerCheckbox, installedVersion, appInfo.latestVersion)
                    }
                }
            }
        }
    }

    private val onDownloadComplete: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            val appInfo = downloadIdToAppInfo[id]
            if (appInfo != null) {
                downloadIdToAppInfo.remove(id)
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
            val selectedModel: String? = dataStoreManager.getSelectedModel().firstOrNull()
            val finalSelectedModel = selectedModel ?: getDeviceName()
            appInfos = versionChecker.getLatestVersionFromServer(finalSelectedModel)
            appInfos.forEach { appInfo ->
                when (appInfo.packageName) {
                    xctrackPackageName -> {
                        val installedVersion = AppUtils.getAppVersion(this@MainActivity, xctrackPackageName)
                        UiUpdater.updateAppInfo(this@MainActivity, xctrackPackageName, xctrackName, xctrackServerVersion, appInfo.latestVersion, null, finalSelectedModel)
                        launch { AppUtils.setAppBackgroundColor(this@MainActivity, xctrackPackageName, xctrackName, installedVersion, finalSelectedModel) }
                        UiUpdater.updateCheckboxState(xctrackPackageName, xctrackCheckbox, installedVersion, appInfo.latestVersion)
                        // Extract and set the APK name
                        val apkName = extractApkName(appInfo.apkPath)
                        xctrackApkName.text = apkName
                        // Set the visibility of the TextView
                        xctrackCheckbox.setOnCheckedChangeListener { _, isChecked ->
                            xctrackApkName.visibility = if (isChecked) View.VISIBLE else View.GONE
                        }
                    }
                    xcguidePackageName -> {
                        val installedVersion = AppUtils.getAppVersion(this@MainActivity, xcguidePackageName)
                        UiUpdater.updateAppInfo(this@MainActivity, xcguidePackageName, xcguideName, xcguideServerVersion, appInfo.latestVersion, null, finalSelectedModel)
                        launch { AppUtils.setAppBackgroundColor(this@MainActivity, xcguidePackageName, xcguideName, installedVersion, finalSelectedModel) }
                        UiUpdater.updateCheckboxState(xcguidePackageName, xcguideCheckbox, installedVersion, appInfo.latestVersion)
                        // Extract and set the APK name
                        val apkName = extractApkName(appInfo.apkPath)
                        xcguideApkName.text = apkName
                        // Set the visibility of the TextView
                        xcguideCheckbox.setOnCheckedChangeListener { _, isChecked ->
                            xcguideApkName.visibility = if (isChecked) View.VISIBLE else View.GONE
                        }
                    }
                    air3managerPackageName -> {
                        val installedVersion = AppUtils.getAppVersion(this@MainActivity, air3managerPackageName)
                        UiUpdater.updateAppInfo(this@MainActivity, air3managerPackageName, air3managerName, air3managerServerVersion, appInfo.latestVersion, null, finalSelectedModel)
                        launch { AppUtils.setAppBackgroundColor(this@MainActivity, air3managerPackageName, air3managerName, installedVersion, finalSelectedModel) }
                        UiUpdater.updateCheckboxState(air3managerPackageName, air3managerCheckbox, installedVersion, appInfo.latestVersion)
                        // Extract and set the APK name
                        val apkName = extractApkName(appInfo.apkPath)
                        air3managerApkName.text = apkName
                        // Set the visibility of the TextView
                        air3managerCheckbox.setOnCheckedChangeListener { _, isChecked ->
                            air3managerApkName.visibility = if (isChecked) View.VISIBLE else View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun handleUpgradeButtonClick() {
        // Display a Toast message
        Toast.makeText(this, "APK download started...", Toast.LENGTH_SHORT).show()
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
        downloadQueue.addAll(appsToUpgrade)
        startNextDownload()
    }

    private fun startNextDownload() {
        if (downloadQueue.isNotEmpty()) {
            val appInfo = downloadQueue.removeAt(0)
            val fullApkUrl = "https://ftp.fly-air3.com${appInfo.apkPath}"
            downloadAndInstallApk(fullApkUrl, appInfo)
        }
    }

    private fun downloadAndInstallApk(apkUrl: String, appInfo: VersionChecker.AppInfo) {
        if (!checkInstallPermission()) {
            showPermissionDialog()
            return
        }

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Downloading ${appInfo.packageName}")
            .setDescription("Downloading the latest version of ${appInfo.packageName}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${appInfo.packageName}.apk")

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadID = downloadManager.enqueue(request)
        downloadIdToAppInfo[downloadID] = appInfo
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
        builder.setTitle("Install Unknown Apps Permission")
            .setMessage("To install apps from unknown sources, you need to allow this permission in your device settings.")
            .setPositiveButton("Settings") { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.data = Uri.parse("package:$packageName")
                    permissionLauncher.launch(intent)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (checkInstallPermission()) {
            startNextDownload()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAppInstallation(packageName: String, nameTextView: TextView, versionTextView: TextView?, selectedModel: String) {
        val installedVersion = AppUtils.getAppVersion(this, packageName)
        val displayedVersion = if (packageName == air3managerPackageName && installedVersion != "N/A") {
            val parts = installedVersion.split(".")
            if (parts.size >= 3) {
                val major = parts[0]
                val minor = parts[1]
                "$major.$minor"
            } else {
                installedVersion
            }
        } else {
            installedVersion
        }
        if (versionTextView != null) {
            versionTextView.text = if (displayedVersion != "N/A") "Installed: $displayedVersion" else "Not installed"
        }
        CoroutineScope(Dispatchers.Main).launch {
            try {
                AppUtils.setAppBackgroundColor(this@MainActivity, packageName, nameTextView, displayedVersion, selectedModel)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
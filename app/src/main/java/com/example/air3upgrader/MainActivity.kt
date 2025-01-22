package com.example.air3upgrader

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.air3upgrader.R.string.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import java.io.File
import kotlinx.coroutines.withContext
import android.widget.ProgressBar
import android.os.Handler
import android.os.Looper
import androidx.glance.visibility

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
    private var appInfos: List<AppInfo> = emptyList() // Corrected type
    private var downloadQueue: MutableList<AppInfo> = mutableListOf()
    private var downloadIdToAppInfo: MutableMap<Long, AppInfo> = mutableMapOf()
    private var isFirstDownload = true

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

        registerReceiver(
            onDownloadComplete,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            RECEIVER_NOT_EXPORTED
        )

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
        checkAppInstallation(xctrackPackageName, xctrackName, xctrackVersion, selectedModel, xctrackPackageName) // Pass packageName
        checkAppInstallation(xcguidePackageName, xcguideName, xcguideVersion, selectedModel, xcguidePackageName) // Pass packageName
        checkAppInstallation(air3managerPackageName, air3managerName, air3managerVersion, selectedModel, air3managerPackageName) // Pass packageName
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
                when (appInfo.name) {
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
            // Inside onDownloadComplete's onReceive method, after calling installApk:
            val progressBar = findViewById<ProgressBar>(R.id.downloadProgressBar)
            progressBar.visibility = View.GONE
            if (appInfo != null) {
                downloadIdToAppInfo.remove(id)
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                val query = DownloadManager.Query()
                query.setFilterById(id)
                val cursor = dm.query(query)
                if (cursor.moveToFirst()) {
                    val columnIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    if (columnIndex != -1) { // Check if the column exists
                        val uriString = cursor.getString(columnIndex)
                        val file = File(Uri.parse(uriString).path!!)
                        installApk(file)
                    } else {
                        // Handle the case where the column is not found
                        Log.e("MainActivity", "COLUMN_LOCAL_URI not found in cursor")
                        // You might want to display an error message to the user here
                        Toast.makeText(context, getString(download_failed), Toast.LENGTH_SHORT).show()
                    }
                }
                cursor.close() // Close the cursor to release resources
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
                supportActionBar?.title = "AIR³ Upgrader - $currentSelectedModel - Android $androidVersion" // Set the title correctly
            }
        }
    }

    private fun getDeviceName(): String {
        return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
    }

    private fun getLatestVersionFromServer() {
        lifecycleScope.launch(Dispatchers.IO) { // Use Dispatchers.IO for network operations
            val selectedModel: String? = dataStoreManager.getSelectedModel().firstOrNull()
            val finalSelectedModel = selectedModel ?: getDeviceName()

            try {
                appInfos = versionChecker.getLatestVersionFromServer(finalSelectedModel)

                // Update UI on the main thread
                withContext(Dispatchers.Main) {
                    appInfos.forEach { appInfo ->
                        when (appInfo.`package`) {
                            xctrackPackageName -> updateAppInfo(appInfo, xctrackName, xctrackServerVersion, xctrackCheckbox, xctrackApkName)
                            xcguidePackageName -> updateAppInfo(appInfo, xcguideName, xcguideServerVersion, xcguideCheckbox, xcguideApkName)
                            air3managerPackageName -> updateAppInfo(appInfo, air3managerName, air3managerServerVersion, air3managerCheckbox, air3managerApkName)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting latest version from server", e)
                // Handle error, e.g., show a toast message to the user
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error getting latest version", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateAppInfo(appInfo: AppInfo, nameTextView: TextView, serverVersionTextView: TextView, checkBox: CheckBox, apkNameTextView: TextView) {
        val installedVersion = AppUtils.getAppVersion(this@MainActivity, appInfo.`package`)
        UiUpdater.updateAppInfo(this@MainActivity, appInfo.`package`, nameTextView, serverVersionTextView, appInfo.latestVersion, null, selectedModel)
        lifecycleScope.launch {
            AppUtils.setAppBackgroundColor(this@MainActivity, appInfo.`package`, nameTextView, installedVersion, selectedModel)
        }
        UiUpdater.updateCheckboxState(appInfo.`package`, checkBox, installedVersion, appInfo.latestVersion)
        val apkName = extractApkName(appInfo.apkPath)
        apkNameTextView.text = apkName
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            apkNameTextView.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
    }

    private fun handleUpgradeButtonClick() {
        lifecycleScope.launch {
            getLatestVersionFromServer() // Fetch the latest app information

            // Wait for the appInfos to be populated
            while (appInfos.isEmpty()) {
                delay(100) // Wait for 100 milliseconds
            }

            // Display a Toast message
            Toast.makeText(this@MainActivity, getString(apk_download_started), Toast.LENGTH_SHORT).show()

            val appsToUpgrade = mutableListOf<AppInfo>()
            if (xctrackCheckbox.isChecked) {
                appInfos.find { appInfo -> appInfo.`package` == xctrackPackageName }?.let { appsToUpgrade.add(it) }
            }
            if (xcguideCheckbox.isChecked) {
                appInfos.find { appInfo -> appInfo.`package` == xcguidePackageName }?.let { appsToUpgrade.add(it) }
            }
            if (air3managerCheckbox.isChecked) {
                appInfos.find { appInfo -> appInfo.`package` == air3managerPackageName }?.let { appsToUpgrade.add(it) }
            }

            if (appsToUpgrade.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(no_apps_selected_for_upgrade), Toast.LENGTH_SHORT).show()
                return@launch
            }

            downloadQueue.addAll(appsToUpgrade)
            startNextDownload()
        }
    }

    private fun startNextDownload() {
        if (downloadQueue.isNotEmpty()) {
            if (!isFirstDownload) {
                // Display a Toast message
                Toast.makeText(this, getString(wait_for_next_download), Toast.LENGTH_SHORT).show() // Use string resource
            } else {
                isFirstDownload = false
            }
            val appInfo = downloadQueue.removeAt(0)
            val fullApkUrl = "https://ftp.fly-air3.com${appInfo.apkPath}"
            downloadAndInstallApk(fullApkUrl, appInfo)
        }
    }

    private fun downloadAndInstallApk(apkUrl: String, appInfo: AppInfo) {
        if (!checkInstallPermission()) {
            showPermissionDialog()
            return
        }

        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle(getString(downloading) + " " + appInfo.name)
            .setDescription(getString(downloading_latest_version_of) + " " + appInfo.name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "${appInfo.`package`}.apk")

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)
        downloadIdToAppInfo[downloadId] = appInfo

        // Display the progress bar
        val progressBar = findViewById<ProgressBar>(R.id.downloadProgressBar)
        progressBar.visibility = View.VISIBLE

        // Create a ContentObserver to monitor download progress
        val contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalBytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)

                    if (bytesDownloadedIndex != -1 && totalBytesIndex != -1) {
                        val bytesDownloaded = cursor.getInt(bytesDownloadedIndex)
                        val totalBytes = cursor.getInt(totalBytesIndex)

                        if (totalBytes > 0) {
                            val progress = (bytesDownloaded * 100 / totalBytes).toInt()
                            progressBar.progress = progress
                        }
                    }
                }
                cursor.close()
            }
        }

        // Register the ContentObserver
        contentResolver.registerContentObserver(Uri.parse("content://downloads/my_downloads"), true, contentObserver)
    }

    private fun installApk(file: File) {
        runOnUiThread {
            val uri = FileProvider.getUriForFile(this, "${this.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setDataAndType(uri, "application/vnd.android.package-archive")
            intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivityForResult(intent, 0) // Use startActivityForResult
        }
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
        builder.setTitle(getString(install_unknown_apps_permission))
            .setMessage(getString(to_install_apps_from_unknown_sources_you_need_to_allow_this_permission_in_your_device_settings))
            .setPositiveButton(getString(settings)) { _, _ ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    intent.data = Uri.parse("package:${this.packageName}") // Use this.packageName
                    permissionLauncher.launch(intent)
                }
            }
            .setNegativeButton(getString(cancel), null)
            .show()
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (checkInstallPermission()) {
            startNextDownload()
        } else {
            Toast.makeText(this, getString(permission_denied), Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAppInstallation(packageName: String, nameTextView: TextView, versionTextView: TextView?, selectedModel: String, packageNameArg: String) {
        // Add packageNameArg
        val installedVersion = AppUtils.getAppVersion(this, packageNameArg) // Use packageNameArg
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
            versionTextView.text = if (displayedVersion != getString(na)) getString(installed) + " " + displayedVersion else getString(not_installed)
        }
        CoroutineScope(Dispatchers.Main).launch {
            try {
                AppUtils.setAppBackgroundColor(this@MainActivity, packageNameArg, nameTextView, displayedVersion, selectedModel) // Use packageNameArg
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
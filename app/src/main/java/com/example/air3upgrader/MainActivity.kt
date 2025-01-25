package com.example.air3upgrader

import android.Manifest
import android.annotation.SuppressLint
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
import java.util.LinkedList
import kotlinx.coroutines.withContext
import android.widget.ProgressBar
import android.os.Handler
import android.os.Looper
import androidx.glance.visibility
import android.content.ActivityNotFoundException
import com.google.android.material.snackbar.Snackbar
import kotlin.collections.isNotEmpty
import kotlin.collections.removeFirst
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.color.DynamicColors
import timber.log.Timber
import com.example.air3upgrader.AppUtils.getServerVersion
import com.example.air3upgrader.UiUpdater

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
    private val REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 1001
    private val REQUEST_CODE_QUERY_ALL_PACKAGES = 1002
    private val REQUEST_CODE_INSTALL_PACKAGES = 1003

    private lateinit var xctrackApkName: TextView
    private lateinit var xcguideApkName: TextView
    private lateinit var air3managerApkName: TextView
    private lateinit var contentObserver: ContentObserver
    private lateinit var downloadCompleteReceiver: DownloadCompleteReceiver // Declare as class-level variable


    private var wakeLock: PowerManager.WakeLock? = null
    private var selectedModel: String = ""
    private var appInfos: List<AppInfo> = emptyList() // Corrected type
    private var downloadQueue: MutableList<AppInfo> = mutableListOf()
    private var downloadIdToAppInfo: MutableMap<Long, AppInfo> = mutableMapOf()
    private var isFirstDownload = true
    private var fileName: String = ""

    // Package names of the apps we want to check
    private val xctrackPackageName = "org.xcontest.XCTrack"
    private val xcguidePackageName = "indysoft.xc_guide"
    private val air3managerPackageName = "com.xc.r3"
    private val versionChecker by lazy { VersionChecker(this) }
    private var downloadID: Long = 0

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.d("MainActivity", "onCreate() called")

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Apply dynamic colors if available
        DynamicColors.applyToActivitiesIfAvailable(application)
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

        // Register the DownloadCompleteReceiver
        downloadCompleteReceiver = DownloadCompleteReceiver()
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)

        // XCTrack Checkbox Listener
        xctrackCheckbox.setOnCheckedChangeListener { _, isChecked ->
            val appInfo = appInfos.find { it.`package` == xctrackPackageName }
            appInfo?.isSelectedForUpgrade = isChecked
            // Trigger UI update
            appInfo?.let {
                UiUpdater.updateAppInfo(this@MainActivity, it, xctrackName, xctrackServerVersion, xctrackVersion, selectedModel)
            }
        }

        xcguideCheckbox.setOnCheckedChangeListener { _, isChecked ->
            val appInfo = appInfos.find { it.`package` == xcguidePackageName }
            appInfo?.isSelectedForUpgrade = isChecked
            // Trigger UI update
            appInfo?.let {
                UiUpdater.updateAppInfo(this@MainActivity, it, xcguideName, xcguideServerVersion, xcguideVersion, selectedModel)
            }
        }

        air3managerCheckbox.setOnCheckedChangeListener { _, isChecked ->
            val appInfo = appInfos.find { it.`package` == air3managerPackageName }
            appInfo?.isSelectedForUpgrade = isChecked
            // Trigger UI update
            appInfo?.let {
                UiUpdater.updateAppInfo(this@MainActivity, it, air3managerName, air3managerServerVersion, air3managerVersion, selectedModel)
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
        Log.d("MainActivity", "onDestroy() called")
        finishAffinity() // Ensure the app is fully closed
        releaseWakeLock()
        unregisterReceiver(downloadCompleteReceiver)
        contentResolver.unregisterContentObserver(contentObserver!!)
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
            val packageNames = listOf(xctrackPackageName, xcguidePackageName, air3managerPackageName)
            val checkboxes = listOf(xctrackCheckbox, xcguideCheckbox, air3managerCheckbox)

            var appInfo: AppInfo? = null // Déclaration de la variable appInfo

            for (i in packageNames.indices) {
                val packageName = packageNames[i]
                val checkbox = checkboxes[i]
                val installedVersion = AppUtils.getAppVersion(this@MainActivity, packageName)
                appInfo = appInfos.find { it.`package` == packageName } // Assignation de la valeur à appInfo
                appInfo?.let {
                    UiUpdater.updateCheckboxState(this@MainActivity, it, checkbox, installedVersion)
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
                supportActionBar?.title = "AIR³ Upgrader - $currentSelectedModel - Android $androidVersion" // Set the title correctly
            }
        }
    }

    private fun getDeviceName(): String {
        return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
    }

    internal fun getLatestVersionFromServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            val selectedModel: String? = dataStoreManager.getSelectedModel().firstOrNull()
            val finalSelectedModel = selectedModel ?: getDeviceName()

            try {
                appInfos = versionChecker.getLatestVersionFromServer(finalSelectedModel)
                AppUtils.appInfos = appInfos // Assign appInfos here
                withContext(Dispatchers.Main) {
                    appInfos.forEach { appInfo ->
                        fileName = appInfo.apkPath.substringAfterLast('/')
                        when (appInfo.`package`) {
                            xctrackPackageName -> UiUpdater.updateAppInfo(this@MainActivity, appInfo, xctrackName, xctrackServerVersion, xctrackVersion, finalSelectedModel)
                            xcguidePackageName -> UiUpdater.updateAppInfo(this@MainActivity, appInfo, xcguideName, xcguideServerVersion, xcguideVersion, finalSelectedModel)
                            air3managerPackageName -> UiUpdater.updateAppInfo(this@MainActivity, appInfo, air3managerName, air3managerServerVersion, air3managerVersion, finalSelectedModel)
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

    private fun enqueueDownload(appInfo: AppInfo) {
        downloadQueue.add(appInfo)
        startNextDownload() // Démarrez le téléchargement immédiatement
    }

    private fun startNextDownload() {
        if (downloadQueue.isNotEmpty()) {
            if (!isFirstDownload) {
                // Display a Toast message
                Toast.makeText(this, getString(wait_for_next_download), Toast.LENGTH_SHORT).show() // Use string resource
            } else {
                isFirstDownload = false
            }
            val appInfo = downloadQueue.removeAt(0) // Use removeAt(0) instead of removeFirst()
            downloadAndInstallApk(appInfo) // Pass only the appInfo
        }
    }

    private fun downloadAndInstallApk(appInfo: AppInfo) {
        Log.d("MainActivity", "downloadAndInstallApk() called for ${appInfo.name}")
        val url = "https://ftp.fly-air3.com${appInfo.apkPath}" // Construct the full URL here
        val originalFileName = appInfo.apkPath.substringAfterLast('/')
        val fileName = if (appInfo.name == "AIR³ Manager") {
            "AIR3Manager.apk" // Use a shorter name for AIR³ Manager
        } else {
            originalFileName // Use the original name for other APKs
        }
        val request = DownloadManager.Request(Uri.parse(url))
            .setDescription("Downloading ${appInfo.name}")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName) // Set the correct file name here

        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadID = downloadManager.enqueue(request)
        Log.d("MainActivity", "Download enqueued with ID: $downloadID")

        downloadIdToAppInfo[downloadID] = appInfo
        // Register ContentObserver
        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d("MainActivity", "ContentObserver onChange() called")
                val query = DownloadManager.Query().setFilterById(downloadID)
                val cursor = downloadManager.query(query)
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    Log.d("MainActivity", "Download status: $status")
                    val bytesDownloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val bytesTotal = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                    Log.d("MainActivity", "Bytes downloaded: $bytesDownloaded, Total bytes: $bytesTotal")
                    val progress = if (bytesTotal > 0) (bytesDownloaded * 100 / bytesTotal).toInt() else 0
                    Log.d("MainActivity", "Download progress: $progress%")
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        contentResolver.unregisterContentObserver(this)
                        Log.d("MainActivity", "ContentObserver unregistered")
                    }
                }
                cursor.close()
            }
        }
        contentResolver.registerContentObserver(Uri.parse("content://downloads/my_downloads"), true, contentObserver)
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

            // Enqueue downloads instead of adding them directly to downloadQueue
            appsToUpgrade.forEach { appInfo ->
                enqueueDownload(appInfo)
            }
        }
    }
    
    private fun installApk(file: File) {
        runOnUiThread {
            val appInfo = downloadIdToAppInfo.values.find { it.`package` == file.nameWithoutExtension }
            val expectedSignature = when (appInfo?.`package`) {
                xctrackPackageName -> getString(R.string.xctrack_apk_signature)
                xcguidePackageName -> getString(R.string.xcguide_apk_signature)
                air3managerPackageName -> getString(R.string.air3manager_apk_signature)
                else -> ""
            }

            if (AppUtils.verifyApkIntegrity(file, expectedSignature)) {
                try {
                    val uri = FileProvider.getUriForFile(this, "${this.packageName}.provider", file)
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, "application/vnd.android.package-archive")
                    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                    try {
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        Timber.e(e, "No activity found to handle installation")
                        Toast.makeText(this, "Error: No activity found to handle installation", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error installing APK")
                    Toast.makeText(this, "Error installing APK", Toast.LENGTH_SHORT).show()
                }
            } else {
                Timber.e("APK integrity check failed")
                Toast.makeText(this, "APK integrity check failed", Toast.LENGTH_SHORT).show()
            }
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

    private fun checkAppInstallation(packageName: String, appNameTextView: TextView, appVersionTextView: TextView, selectedModel: String, appPackageName: String) {
        val installedVersion = AppUtils.getAppVersion(this, packageName)
        appVersionTextView.text = installedVersion

        lifecycleScope.launch {
            val serverVersion = AppUtils.getServerVersion(this@MainActivity, packageName, selectedModel)
            if (serverVersion != null) {
                when (packageName) {
                    xctrackPackageName -> xctrackServerVersion.text = serverVersion
                    xcguidePackageName -> xcguideServerVersion.text = serverVersion
                    air3managerPackageName -> air3managerServerVersion.text = serverVersion
                }

                if (VersionComparator.isServerVersionHigher(installedVersion, serverVersion, packageName)) {
                    // Une nouvelle version est disponible, cocher la case "Upgrade"
                    when (packageName) {
                        xctrackPackageName -> xctrackCheckbox.isChecked = true
                        xcguidePackageName -> xcguideCheckbox.isChecked = true
                        air3managerPackageName -> air3managerCheckbox.isChecked = true
                    }
                } else {
                    // La version du serveur est la même que celle installée, laisser la case décochée
                    when (packageName) {
                        xctrackPackageName -> xctrackCheckbox.isChecked = false
                        xcguidePackageName -> xcguideCheckbox.isChecked = false
                        air3managerPackageName -> air3managerCheckbox.isChecked = false
                    }
                    // Activer la case pour permettre à l'utilisateur de la sélectionner manuellement
                    when (packageName) {
                        xctrackPackageName -> xctrackCheckbox.isEnabled = true
                        xcguidePackageName -> xcguideCheckbox.isEnabled = true
                        air3managerPackageName -> air3managerCheckbox.isEnabled = true
                    }
                }
            } else {
                // Gérer le cas où la version du serveur n'est pas disponible
                when (packageName) {
                    xctrackPackageName -> xctrackServerVersion.text = getString(R.string.version_not_found)
                    xcguidePackageName -> xcguideServerVersion.text = getString(R.string.version_not_found)
                    air3managerPackageName -> air3managerServerVersion.text = getString(R.string.version_not_found)
                }
            }

            AppUtils.setAppBackgroundColor(this@MainActivity, appPackageName, appNameTextView, installedVersion, selectedModel)
        }
    }
}
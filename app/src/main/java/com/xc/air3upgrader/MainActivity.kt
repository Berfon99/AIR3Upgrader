package com.xc.air3upgrader

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import java.util.LinkedList
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    companion object {
        private const val SETTINGS_REQUEST_CODE = 1
    }

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
    private lateinit var refreshButton: Button
    private lateinit var dataStoreManager: DataStoreManager
    private val REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 1001
    private val REQUEST_CODE_QUERY_ALL_PACKAGES = 1002
    private val REQUEST_CODE_INSTALL_PACKAGES = 1003

    private lateinit var xctrackApkName: TextView
    private lateinit var xcguideApkName: TextView
    private lateinit var air3managerApkName: TextView
    private lateinit var downloadCompleteReceiver: DownloadReceiver // Declare as class-level variable


    private var wakeLock: PowerManager.WakeLock? = null
    private var selectedModel: String = ""
    private var appInfos: List<AppInfo> = emptyList() // Corrected type
    private val downloadQueue = LinkedList<AppInfo>()
    private var downloadIdToAppInfo: MutableMap<Long, AppInfo> = mutableMapOf()
    private var fileName: String = ""
    internal var isInstalling = false

    // Package names of the apps we want to check
    private val xctrackPackageName = "org.xcontest.XCTrack"
    private val xcguidePackageName = "indysoft.xc_guide"
    private val air3managerPackageName = "com.xc.r3"
    private val versionChecker by lazy { VersionChecker(this) }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Set the status and navigation bar color
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)
        Timber.d("onCreate() called")

        // Request storage permission
        requestStoragePermission()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        dataStoreManager = DataStoreManager(this)

        // Set the action bar title with device info
        setActionBarTitleWithSelectedModel()

        // Initialize TextViews and Checkboxes
        xctrackName = findViewById(R.id.xctrack_name)
        xcguideName = findViewById(R.id.xcguide_name)
        air3managerName = findViewById(R.id.air3manager_name)
        closeButton = findViewById(R.id.close_button)
        upgradeButton = findViewById(R.id.upgrade_button)
        refreshButton = findViewById(R.id.refresh_button)
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

        refreshButton.setOnClickListener {
            handleRefreshButtonClick()
        }

        // Get the latest version from the server
        getLatestVersionFromServer()

        // Keep the screen on
        acquireWakeLock()

        // Register the DownloadCompleteReceiver
        downloadCompleteReceiver = DownloadReceiver()
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_EXPORTED)

        // Set up checkbox listeners
        setupCheckboxListener(xctrackCheckbox, xctrackPackageName, xctrackName, xctrackServerVersion, xctrackVersion)
        setupCheckboxListener(xcguideCheckbox, xcguidePackageName, xcguideName, xcguideServerVersion, xcguideVersion)
        setupCheckboxListener(air3managerCheckbox, air3managerPackageName, air3managerName, air3managerServerVersion, air3managerVersion)
    }

    private fun setupCheckboxListener(
        checkBox: CheckBox,
        packageName: String,
        nameTextView: TextView,
        serverVersionTextView: TextView,
        installedVersionTextView: TextView
    ) {
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            val appInfo = appInfos.firstOrNull { it.`package` == packageName }
            if (appInfo != null) {
                appInfo.isSelectedForUpgrade = isChecked
                // Trigger UI update
                UiUpdater.updateAppInfo(this@MainActivity, appInfo, nameTextView, serverVersionTextView, installedVersionTextView)
            } else {
                Timber.w("App info not found for package: $packageName")
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
                startActivityForResult(intent, SETTINGS_REQUEST_CODE)
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_WRITE_EXTERNAL_STORAGE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Storage permission granted
                    Log.d("MainActivity", "Storage permission granted")
                    // Now check for install permission
                    if (!checkInstallPermission()) {
                        requestInstallPermission()
                    }
                } else {
                    // Storage permission denied
                    Log.d("MainActivity", "Storage permission denied")
                    // Handle the case where the user denied storage permission
                    Toast.makeText(this, "Storage permission is required to download and install apps.", Toast.LENGTH_LONG).show()
                }
            }
            // ... other permission requests ...
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST_CODE) {
            if (resultCode == SettingsActivity.MODEL_CHANGED_RESULT_CODE) {
                // Trigger your refresh logic here
                refreshData()
            }
        }
        if (requestCode == REQUEST_CODE_INSTALL_PACKAGES) {
            if (checkInstallPermission()) {
                // Install permission granted
                Log.d("MainActivity", "Install permission granted")
            } else {
                // Install permission denied
                Log.d("MainActivity", "Install permission denied")
                // Handle the case where the user denied install permission
                Toast.makeText(this, "Install permission is required to install apps.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun refreshData() {
        // Example: Re-fetch server versions (replace with your actual code)
        getLatestVersionFromServer()
        setActionBarTitleWithSelectedModel()
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "AIR3Upgrader:KeepScreenOn"
        )
        wakeLock?.acquire()
    }

    private fun setActionBarTitleWithSelectedModel() {
        lifecycleScope.launch {
            // Delay the initial read to allow SettingsActivity to initialize
            val selectedModel = dataStoreManager.getSelectedModel().firstOrNull()
            val deviceModel = Build.MODEL
            val finalSelectedModel = when {
                selectedModel == null -> deviceModel
                selectedModel.isEmpty() -> deviceModel
                dataStoreManager.isDeviceModelSupported(selectedModel, getSettingsAllowedModels()) -> selectedModel
                else -> {
                    Log.e("MainActivity", "Unsupported model selected: $selectedModel")
                    getDeviceName()
                }
            }
            dataStoreManager.getSelectedModel().collectLatest { selectedModel ->
                val androidVersion = Build.VERSION.RELEASE // Get the Android version
                supportActionBar?.title = "AIR³ Upgrader - $finalSelectedModel - Android $androidVersion" // Set the title correctly
            }
        }
    }

    private fun getDefaultModel(): String {
        val deviceModel = Build.MODEL
        return if (dataStoreManager.isDeviceModelSupported(deviceModel, getSettingsAllowedModels())) {
            deviceModel
        } else {
            getDeviceName()
        }
    }

    private fun getDeviceName(): String {
        return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: Build.MODEL
    }

    private fun getSettingsAllowedModels(): List<String> {
        val settingsActivity = SettingsActivity()
        return settingsActivity.getAllowedModels()
    }

    private fun checkAppInstallation() {
        Log.d("MainActivity", "checkAppInstallation() called")
        lifecycleScope.launch {
            val selectedModel: String? = dataStoreManager.getSelectedModel().firstOrNull()
            val finalSelectedModel = when {
                selectedModel == null -> getDefaultModel()
                dataStoreManager.isDeviceModelSupported(selectedModel, getSettingsAllowedModels()) -> selectedModel
                else -> getDefaultModel()
            }
            Log.d("MainActivity", "checkAppInstallation() - Selected model: $finalSelectedModel")
            checkAppInstallationForApp(xctrackPackageName, xctrackName, xctrackVersion, finalSelectedModel, xctrackPackageName)
            checkAppInstallationForApp(xcguidePackageName, xcguideName, xcguideVersion, finalSelectedModel, xcguidePackageName)
            checkAppInstallationForApp(air3managerPackageName, air3managerName, air3managerVersion, finalSelectedModel, air3managerPackageName)
        }
    }

    private fun checkAppInstallationForApp(packageName: String, appNameTextView: TextView, appVersionTextView: TextView, selectedModel: String, appPackageName: String) {
        Log.d("MainActivity", "checkAppInstallationForApp() called for package: $packageName")
        val installedVersion = AppUtils.getAppVersion(this, packageName)
        Log.d("MainActivity", "  Installed version for $packageName: $installedVersion")
        appVersionTextView.text = if (installedVersion != getString(R.string.na)) getString(R.string.installed) + " " + installedVersion else getString(R.string.not_installed)

        lifecycleScope.launch {
            val appInfo = appInfos.find { it.`package` == packageName }
            Log.d("MainActivity", "  appInfo for $packageName: $appInfo")
            val serverVersion = appInfo?.highestServerVersion
            Log.d("MainActivity", "  Server version for $packageName: $serverVersion")
            if (serverVersion != null) {
                val serverVersionToDisplay = if (packageName == xctrackPackageName) {
                    serverVersion.replace("-", ".")
                } else {
                    serverVersion
                }
                when (packageName) {
                    xctrackPackageName -> xctrackServerVersion.text = getString(R.string.server) + " " + serverVersionToDisplay
                    xcguidePackageName -> xcguideServerVersion.text = getString(R.string.server) + " " + serverVersionToDisplay
                    air3managerPackageName -> air3managerServerVersion.text = getString(R.string.server) + " " + serverVersionToDisplay
                }

                Log.d("MainActivity", "  Calling VersionComparator.isServerVersionHigher() with: installedVersion=$installedVersion, serverVersion=$serverVersion, packageName=$packageName")
                if (VersionComparator.isServerVersionHigher(installedVersion, serverVersion, packageName)) {
                    Log.d("MainActivity", "  Server version is higher for $packageName")
                    // Une nouvelle version est disponible, cocher la case "Upgrade"
                    when (packageName) {
                        xctrackPackageName -> xctrackCheckbox.isChecked = true
                        xcguidePackageName -> xcguideCheckbox.isChecked = true
                        air3managerPackageName -> air3managerCheckbox.isChecked = true
                    }
                } else {
                    Log.d("MainActivity", "  Server version is not higher for $packageName")
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
                Log.d("MainActivity", "  Server version is null for $packageName")
                // Gérer le cas où la version du serveur n'est pas disponible
                when (packageName) {
                    xctrackPackageName -> xctrackServerVersion.text = getString(R.string.version_not_found)
                    xcguidePackageName -> xcguideServerVersion.text = getString(R.string.version_not_found)
                    air3managerPackageName -> air3managerServerVersion.text = getString(R.string.version_not_found)
                }
            }
            launch {
                Log.d("MainActivity", "Before calling setAppBackgroundColor")
                val appInfo = appInfos.find { it.`package` == packageName }
                if (appInfo != null) {
                    UiUpdater.setAppBackgroundColor(this@MainActivity, appInfo, appNameTextView, appVersionTextView)
                } else {
                    Log.e("MainActivity", "AppInfo is null for package: $packageName")
                }
                Log.d("MainActivity", "After calling setAppBackgroundColor")
            }
        }
    }

    private fun enqueueDownload(appInfo: AppInfo) {
        Log.d("MainActivity", "enqueueDownload() called for ${appInfo.name} with apkPath: ${appInfo.apkPath}")
        downloadQueue.add(appInfo)
        if (downloadQueue.size == 1) {
            downloadNextApp()
        }
    }

    internal fun downloadNextApp() {
        Log.d("MainActivity", "downloadNextApp() called")
        if (downloadQueue.isNotEmpty()) {
            val nextApp = downloadQueue.first()
            downloadQueue.removeFirst()
            downloadAndInstallApk(nextApp)
        }
    }

    private fun downloadAndInstallApk(appInfo: AppInfo) {
        Log.d("MainActivity", "downloadAndInstallApk() called for ${appInfo.name} with apkPath: ${appInfo.apkPath}")
        val url = if (appInfo.apkPath.startsWith("http")) {
            appInfo.apkPath
        } else {
            "https://ftp.fly-air3.com${appInfo.apkPath}" // Construct the full URL here
        }
        val fileName = when {
            appInfo.name == "AIR³ Manager" -> {
                "AIR3Manager.apk" // Use a shorter name for AIR³ Manager
            }
            appInfo.`package` == "indysoft.xc_guide" && !appInfo.apkPath.startsWith("/") -> {
                "${appInfo.name}.apk" // Use the app name (e.g., "XCGuide-608.apk") for XC Guide from pg-race.aero
            }
            else -> {
                appInfo.apkPath.substringAfterLast('/') // Use the original name for other APKs and XC Guide from ftp.fly-air3.com
            }
        }
        Log.d("MainActivity", "Downloading from URL: $url, saving as: $fileName")

        val request = DownloadManager.Request(Uri.parse(url))
            .setDescription(appInfo.`package`) // Set the description to the package name
            .setTitle(appInfo.name) // Set the title to the app name
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName) // Set the correct file name here
        Log.d("MainActivity", "Request: $request")
        val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        try {
            downloadManager.enqueue(request)
            Log.d("MainActivity", "Download enqueued")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error enqueuing download", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("MainActivity", "onDestroy() called")
        finishAffinity() // Ensure the app is fully closed
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        unregisterReceiver(downloadCompleteReceiver)
    }

    internal fun getLatestVersionFromServer() {
        Log.d("MainActivity", "getLatestVersionFromServer() called")
        lifecycleScope.launch {
            // Check if internet is available
            if (!NetworkUtils.isNetworkAvailable(this@MainActivity)) {
                Log.w("MainActivity", "No internet connection")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, getString(R.string.no_internet_connection), Toast.LENGTH_SHORT).show()
                    showNoInternetDialog()
                }
                return@launch
            }
            val selectedModel: String? = dataStoreManager.getSelectedModel().firstOrNull()
            val finalSelectedModel = when {
                selectedModel == null -> getDefaultModel()
                dataStoreManager.isDeviceModelSupported(selectedModel, getSettingsAllowedModels()) -> selectedModel
                else -> getDefaultModel()
            }
            Log.d("MainActivity", "Selected model: $finalSelectedModel")

            try {
                Log.d("MainActivity", "Fetching latest version from server...")
                val newAppInfos = withContext(Dispatchers.IO) {
                    versionChecker.getLatestVersionFromServer(finalSelectedModel)
                }
                if (newAppInfos.isEmpty()) {
                    Log.e("MainActivity", "getLatestVersionFromServer: Server returned an empty list")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Error getting latest version", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                appInfos = newAppInfos
                Log.d("MainActivity", "Successfully fetched ${appInfos.size} app infos from server")
                for (appInfo in appInfos) {
                    Log.d("MainActivity", "AppInfo: ${appInfo.name}, Package: ${appInfo.`package`}, APK Path: ${appInfo.apkPath}, Highest Server Version: ${appInfo.highestServerVersion}")
                }
                withContext(Dispatchers.Main) {
                    checkAppInstallation()
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting latest version from server: ${e.message}")
                // Handle error, e.g., show a toast message to the user
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error getting latest version", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleUpgradeButtonClick() {
        lifecycleScope.launch {
            selectedModel = when {
                dataStoreManager.getSelectedModel().firstOrNull() == null -> getDefaultModel()
                dataStoreManager.isDeviceModelSupported(dataStoreManager.getSelectedModel().firstOrNull()!!, getSettingsAllowedModels()) -> dataStoreManager.getSelectedModel().firstOrNull()!!
                else -> getDefaultModel()
            }
            if (!NetworkUtils.isNetworkAvailable(this@MainActivity)) {
                showNoInternetDialog()
                return@launch
            }
            // Fetch the latest app information FIRST
            getLatestVersionFromServer()

            val appsToUpgrade = mutableListOf<AppInfo>()
            if (xctrackCheckbox.isChecked) {
                appInfos.find { appInfo -> appInfo.`package` == xctrackPackageName }?.let {
                    Log.d("MainActivity", "XCTrack apkPath before enqueue: ${it.apkPath}")
                    appsToUpgrade.add(it)
                }
            }
            if (xcguideCheckbox.isChecked) {
                appInfos.find { appInfo -> appInfo.`package` == xcguidePackageName }?.let {
                    Log.d("MainActivity", "XCGuide apkPath before enqueue: ${it.apkPath}")
                    appsToUpgrade.add(it)
                }
            }
            if (air3managerCheckbox.isChecked) {
                appInfos.find { appInfo -> appInfo.`package` == air3managerPackageName }?.let {
                    Log.d("MainActivity", "AIR3Manager apkPath before enqueue: ${it.apkPath}")
                    appsToUpgrade.add(it)
                }
            }

            if (appsToUpgrade.isEmpty()) {
                Toast.makeText(this@MainActivity, getString(R.string.no_apps_selected_for_upgrade), Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Enqueue downloads instead of adding them directly to downloadQueue
            appsToUpgrade.forEach { appInfo ->
                enqueueDownload(appInfo)
            }
        }
    }

    private fun handleRefreshButtonClick() {
        lifecycleScope.launch {
            if (!NetworkUtils.isNetworkAvailable(this@MainActivity)) {
                showNoInternetDialog()
                return@launch
            }
            getLatestVersionFromServer()
        }
    }

    private fun showNoInternetDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.no_internet_connection))
            .setMessage(getString(R.string.no_internet_message))
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                // Update UI to show "version not found"
                updateUIAfterNoInternet()
            }
            .setNegativeButton(getString(R.string.retry)) { dialog, _ ->
                dialog.dismiss()
                // Retry the process
                getLatestVersionFromServer()
            }
            .setCancelable(false) // Prevent dismissing by tapping outside
            .create() // Create the dialog
        dialog.show() // Show the dialog
    }

    private fun updateUIAfterNoInternet() {
        xctrackVersion.text = getString(R.string.version_not_found)
        xcguideVersion.text = getString(R.string.version_not_found)
        air3managerVersion.text = getString(R.string.version_not_found)
        xctrackServerVersion.text = getString(R.string.version_not_found)
        xcguideServerVersion.text = getString(R.string.version_not_found)
        air3managerServerVersion.text = getString(R.string.version_not_found)
    }

    private fun checkInstallPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val packageManager = packageManager
            return packageManager.canRequestPackageInstalls()
        }
        return true // No need to check on older versions
    }

    private fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = Uri.parse("package:$packageName")
            startActivityForResult(intent, REQUEST_CODE_INSTALL_PACKAGES)
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted, request it
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_EXTERNAL_STORAGE)
            } else {
                // Permission is already granted
                Log.d("MainActivity", "Storage permission already granted")
                // Check for install permission
                if (!checkInstallPermission()) {
                    requestInstallPermission()
                }
            }
        } else {
            // Permission is already granted
            Log.d("MainActivity", "Storage permission already granted")
            // Check for install permission
            if (!checkInstallPermission()) {
                requestInstallPermission()
            }
        }
    }
}

package com.xc.air3upgrader

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.ui.geometry.isEmpty
import java.util.LinkedList

import kotlinx.coroutines.flow.first


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

    private lateinit var xctrackApkName: TextView
    private lateinit var xcguideApkName: TextView
    private lateinit var air3managerApkName: TextView
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var downloadCompleteReceiver: DownloadCompleteReceiver

    private var wakeLock: PowerManager.WakeLock? = null
    private var selectedModel: String = ""
    private var appInfos: List<AppInfo> = emptyList() // Corrected type
    private var downloadIdToAppInfo: MutableMap<Long, AppInfo> = mutableMapOf()
    private var fileName: String = ""
    internal var isInstalling = false

    // Package names of the apps we want to check
    private val xctrackPackageName = "org.xcontest.XCTrack"
    private val xcguidePackageName = "indysoft.xc_guide"
    private val air3managerPackageName = "com.xc.r3"
    private val versionChecker by lazy { VersionChecker(this) }
    private var onCreateCounter = 0
    private var isFirstLaunch = true

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate: called")
        onCreateCounter++
        Timber.d("onCreate: onCreate() called - Count: $onCreateCounter")
        Timber.d("onCreate: savedInstanceState is null: ${savedInstanceState == null}")
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        dataStoreManager = DataStoreManager(this)
        permissionsManager = PermissionsManager(this, dataStoreManager)
        downloadCompleteReceiver = DownloadCompleteReceiver()

        // Register the ActivityResultLauncher in onCreate()
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (!isGranted) {
                permissionsManager.showNotificationPermissionDeniedMessage()
            }
            Timber.d("Notification permission granted")
        }


        // Check if the app was launched manually
        val isManualLaunchFromIntent =
            intent.action == Intent.ACTION_MAIN && intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
        Timber.d("onCreate: isManualLaunchFromIntent: $isManualLaunchFromIntent")
        if (isManualLaunchFromIntent) {
            permissionsManager.checkAllPermissionsGrantedAndContinue(requestPermissionLauncher)
        } else {
            lifecycleScope.launch {
                val isManualLaunch: Boolean = dataStoreManager.getIsManualLaunch().firstOrNull() ?: false
                val unhiddenLaunchOnReboot: Boolean = dataStoreManager.getUnhiddenLaunchOnReboot().firstOrNull() ?: false

                Timber.d("onCreate: isManualLaunch from DataStore: $isManualLaunch")
                Timber.d("onCreate: unhiddenLaunchOnReboot from DataStore: $unhiddenLaunchOnReboot")

                if (!isManualLaunch && !unhiddenLaunchOnReboot) {
                    Timber.d("App launched hidden, finishing activity")
                    finish()
                    return@launch
                } else {
                    permissionsManager.checkAllPermissionsGrantedAndContinue(requestPermissionLauncher)
                }
            }
        }
        Timber.d("onCreate: end")
    }
    override fun onResume() {
        super.onResume()
        Timber.d("onResume: called")

        val intentFilter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            ContextCompat.registerReceiver(
                this,
                downloadCompleteReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33
            ContextCompat.registerReceiver(this, downloadCompleteReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        } else {
            Timber.d("onResume: Registering downloadCompleteReceiver")
            registerReceiver(downloadCompleteReceiver, intentFilter)
        }

        if (permissionsManager.checkAllPermissionsGranted()) {
            continueSetup()
        }
    }
    private fun showUI() {
        Timber.d("showUI: called")
        setContentView(R.layout.activity_main)
        // Set the status and navigation bar color
        window.statusBarColor = ContextCompat.getColor(this@MainActivity, R.color.black)
        window.navigationBarColor = ContextCompat.getColor(this@MainActivity, R.color.black)
        Timber.d("showUI: setContentView() called")

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        scheduleUpgradeCheck()
        // Set the action bar title with device info
        lifecycleScope.launch {
            setActionBarTitleWithSelectedModel()
        }

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
        lifecycleScope.launch {
            getLatestVersionFromServer()
        }

        // Keep the screen on
        acquireWakeLock()

// Set up checkbox listeners
        setupCheckboxListener(xctrackCheckbox, xctrackPackageName, xctrackName, xctrackServerVersion, xctrackVersion)
        setupCheckboxListener(xcguideCheckbox, xcguidePackageName, xcguideName, xcguideServerVersion, xcguideVersion)
        setupCheckboxListener(air3managerCheckbox, air3managerPackageName, air3managerName, air3managerServerVersion, air3managerVersion)
        Timber.d("showUI: end")
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Timber.d("onSaveInstanceState: called")
    }
    private fun continueSetup() {
        Timber.d("continueSetup: called")
        val isManualLaunchFromIntent = intent.action == Intent.ACTION_MAIN && intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
        if (isManualLaunchFromIntent) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    dataStoreManager.saveIsManualLaunch(false)
                }
            }
        }
        showUI()
        Timber.d("continueSetup: end")
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
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Timber.d("onActivityResult: called")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SETTINGS_REQUEST_CODE) {
            if (resultCode == SettingsActivity.MODEL_CHANGED_RESULT_CODE) {
                // Trigger your refresh logic here
                refreshData()
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
        UiUpdater.setActionBarTitleWithSelectedModel(this, dataStoreManager, ::getSettingsAllowedModels, ::getDeviceName, lifecycleScope, supportActionBar)
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
        UiUpdater.checkAppInstallationForApp(this, packageName, appNameTextView, appVersionTextView, appInfos, xctrackPackageName, xctrackServerVersion, xctrackCheckbox, xcguidePackageName, xcguideServerVersion, xcguideCheckbox, air3managerPackageName, air3managerServerVersion, air3managerCheckbox, lifecycleScope)
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
                dataStoreManager.isDeviceModelSupported(
                    dataStoreManager.getSelectedModel().firstOrNull()!!,
                    getSettingsAllowedModels()
                ) -> dataStoreManager.getSelectedModel().firstOrNull()!!

                else -> getDefaultModel()
            }
            if (!NetworkUtils.isNetworkAvailable(this@MainActivity)) {
                showNoInternetDialog()
                return@launch
            }
            downloadCompleteReceiver = DownloadCompleteReceiver()
            // Request storage permission before proceeding
            permissionsManager.requestStoragePermission {
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
                // Enqueue downloads instead of adding them directly to downloadQueue
                appsToUpgrade.forEach { appInfo ->
                    downloadCompleteReceiver.enqueueDownload(this@MainActivity, downloadCompleteReceiver.downloadQueue, appInfo)
                }
                // Start all downloads
                downloadCompleteReceiver.downloadNextApp(this@MainActivity)
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
        UiUpdater.updateUIAfterNoInternet(xctrackVersion, xcguideVersion, air3managerVersion, xctrackServerVersion, xcguideServerVersion, air3managerServerVersion, this)
    }
    private fun scheduleUpgradeCheck() {
        val dataStoreManager = DataStoreManager(this)
        val context = this // Get the context here
        lifecycleScope.launch {
            val interval = dataStoreManager.getUpgradeCheckInterval().firstOrNull() ?: Interval(0, 0, 0)
            val periodicWorkRequest = PeriodicWorkRequest.Builder(
                UpgradeCheckWorker::class.java,
                interval.days.toLong(),
                TimeUnit.DAYS
            )
                .setInitialDelay(interval.minutes.toLong(), TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork( // Use the context here
                "UpgradeCheck",
                ExistingPeriodicWorkPolicy.KEEP,
                periodicWorkRequest
            )
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        Timber.d("onDestroy: called")
        finishAffinity() // Ensure the app is fully closed
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }
    override fun onStart() {
        super.onStart()
        Timber.d("onStart: called")
    }
    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(downloadCompleteReceiver)
            Timber.d("Receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Timber.w("Receiver was not registered: ${e.message}")
        }
    }

    override fun onStop() {
        super.onStop()
        Timber.d("onStop: called")
    }
    fun testSendDownloadCompleteBroadcast() {
        val intent = Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        sendBroadcast(intent)
        Timber.d("Manually sent DOWNLOAD_COMPLETE broadcast")
    }
}
package com.xc.air3upgrader

import android.content.Context
import android.content.Intent
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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.red
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), NetworkUtils.NetworkDialogListener {

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
    //private lateinit var downloadCompleteReceiver: DownloadCompleteReceiver

    private var wakeLock: PowerManager.WakeLock? = null
    private var selectedModel: String = ""
    private var appInfos: List<AppInfo> = emptyList() // Corrected type
    private var downloadIdToAppInfo: MutableMap<Long, AppInfo> = mutableMapOf()
    private var fileName: String = ""
    internal var isInstalling = false
    var noInternetAgreed: Boolean = false

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
        installSplashScreen()
        super.onCreate(savedInstanceState)
        noInternetAgreed = false
        dataStoreManager = DataStoreManager(this)
        // Initialize the DataStore
        dataStoreManager.initializeDataStore()
        val isManualLaunchFromIntent =
            intent.action == Intent.ACTION_MAIN && intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
        permissionsManager = PermissionsManager(this)
        lifecycleScope.launch {
            val isFirstLaunch = dataStoreManager.getIsFirstLaunch().firstOrNull() ?: true
            var currentIsManualLaunch = false
            var isLaunchFromCheckPromptActivity = false
            if (isFirstLaunch && isManualLaunchFromIntent) {
                dataStoreManager.saveIsManualLaunch(true)
                currentIsManualLaunch = true
            }
            if (isManualLaunchFromIntent) {
                dataStoreManager.saveIsManualLaunch(true)
                currentIsManualLaunch = true
            }
            if (!isManualLaunchFromIntent) {
                dataStoreManager.saveIsManualLaunch(false)
                currentIsManualLaunch = false
            }
            // Check if the app is launched from CheckPromptActivity
            isLaunchFromCheckPromptActivity = intent.getBooleanExtra("isLaunchFromCheckPromptActivity", false)
            val unhiddenLaunchOnReboot: Boolean = dataStoreManager.getUnhiddenLaunchOnReboot().firstOrNull() ?: false
            val isAutomaticUpgradeReminderEnabled: Boolean = dataStoreManager.getAutomaticUpgradeReminder().firstOrNull() ?: false
            if (isManualLaunchFromIntent || currentIsManualLaunch) {
                acquireWakeLock()
            }
            if (!currentIsManualLaunch && unhiddenLaunchOnReboot && isAutomaticUpgradeReminderEnabled) {
                Timber.d("App launched unhidden, launching CheckPromptActivity")
                val intent = Intent(this@MainActivity, CheckPromptActivity::class.java)
                startActivity(intent)
                return@launch
            }
            // Modify the condition to check for isLaunchFromCheckPromptActivity
            if (!currentIsManualLaunch && !unhiddenLaunchOnReboot && !isLaunchFromCheckPromptActivity) {
                Timber.d("App launched hidden, finishing activity")
                finish()
                return@launch
            }
        }

        // Register the ActivityResultLauncher in onCreate()
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Timber.d("Notification permission granted")
                continueSetup()
            } else {
                permissionsManager.showNotificationPermissionDeniedMessage()
                finish()
            }
        }
        // Check if the app was launched manually
        Timber.d("onCreate: isManualLaunchFromIntent: $isManualLaunchFromIntent")
        // Check permissions and continue
        if (permissionsManager.checkAllPermissionsGranted()) {
            continueSetup()
        } else {
            permissionsManager.checkAllPermissionsGrantedAndContinue(requestPermissionLauncher) {
                continueSetup()
            }
        }
        lifecycleScope.launch {
            val isFirstLaunchDataStore = dataStoreManager.getIsFirstLaunch().firstOrNull() ?: true
            if (!isFirstLaunchDataStore) {
                isFirstLaunch = false
            }
        }
        Timber.d("onCreate: end")
    }
    override fun onNoInternetAgreed() {
        Timber.d("onNoInternetAgreed: called")
        noInternetAgreed = true
        showUI()
    }
    internal fun continueSetup() {
        Timber.d("continueSetup: called")
        setContentView(R.layout.activity_main)
        lifecycleScope.launch {
            val isWifiOnly = dataStoreManager.getWifiOnly().firstOrNull() ?: false
            val isNetworkOk = NetworkUtils.checkNetworkAndContinue(
                this@MainActivity,
                isWifiOnly,
                retryAction = {
                    continueSetup()
                },
                listener = this@MainActivity
            )
            if (!isNetworkOk) {
                Timber.d("continueSetup: No network available")
            } else {
                Timber.d("continueSetup: Network available")
                withContext(Dispatchers.IO) {
                    dataStoreManager.saveIsManualLaunch(false)
                }
                val isManualLaunchFromIntent =
                    intent.action == Intent.ACTION_MAIN && intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
                val isManualLaunch: Boolean = dataStoreManager.getIsManualLaunch().firstOrNull() ?: false
                val unhiddenLaunchOnReboot: Boolean =
                    dataStoreManager.getUnhiddenLaunchOnReboot().firstOrNull() ?: false
                val isLaunchFromCheckPromptActivity = intent.getBooleanExtra("isLaunchFromCheckPromptActivity", false)
                // Modify the condition to include isLaunchFromCheckPromptActivity
                if (isManualLaunchFromIntent || (!isManualLaunch && unhiddenLaunchOnReboot) || isLaunchFromCheckPromptActivity) {
                    val success = getLatestVersionFromServer()
                    if (!success) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                this@MainActivity,
                                "Unable to fetch server versions.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
                showUI()
            }
        }
        Timber.d("continueSetup: end")
    }
    private fun showUI() {
        Timber.d("showUI: called")
        //setContentView(R.layout.activity_main) // Removed this line
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
        lifecycleScope.launch {
            checkAppInstallation()
        }
        lifecycleScope.launch {
            val isWifiOnlyEnabled = dataStoreManager.getWifiOnly().firstOrNull() ?: false
            if (!noInternetAgreed) {
                // Get the latest version from the server
                val isNetworkAvailable = NetworkUtils.isNetworkAvailable(this@MainActivity)
                val isWifiConnected = NetworkUtils.isWifiConnected(this@MainActivity)
                if (isFirstLaunch) {
                    if (isNetworkAvailable) {
                        try {
                            getLatestVersionFromServer()
                        } finally {
                            isFirstLaunch = false
                        }
                    } else {
                        xctrackServerVersion.text = getString(R.string.not_available)
                        xcguideServerVersion.text = getString(R.string.not_available)
                        air3managerServerVersion.text = getString(R.string.not_available)
                        xctrackServerVersion.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gray))
                        xcguideServerVersion.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gray))
                        air3managerServerVersion.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gray))
                    }
                } else {
                    if ((isWifiOnlyEnabled && isWifiConnected) || (!isWifiOnlyEnabled && isNetworkAvailable)) {
                        try {
                            getLatestVersionFromServer()
                        } finally {
                            //withContext(Dispatchers.Main) {
                            //    checkAppInstallation()
                            //}
                        }
                    } else {
                        xctrackServerVersion.text = getString(R.string.not_available)
                        xcguideServerVersion.text = getString(R.string.not_available)
                        air3managerServerVersion.text = getString(R.string.not_available)
                        xctrackServerVersion.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gray))
                        xcguideServerVersion.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gray))
                        air3managerServerVersion.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gray))
                    }
                }
            } else {
                xctrackCheckbox.isChecked = false
                xcguideCheckbox.isChecked = false
                air3managerCheckbox.isChecked = false
                xctrackServerVersion.text = getString(R.string.not_available)
                xcguideServerVersion.text = getString(R.string.not_available)
                air3managerServerVersion.text = getString(R.string.not_available)
                xctrackServerVersion.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gray))
                xcguideServerVersion.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gray))
                air3managerServerVersion.setTextColor(ContextCompat.getColor(this@MainActivity, R.color.gray))
            }
        }
        // Set up checkbox listeners
        setupCheckboxListener(xctrackCheckbox, xctrackPackageName, xctrackName, xctrackServerVersion, xctrackVersion)
        setupCheckboxListener(xcguideCheckbox, xcguidePackageName, xcguideName, xcguideServerVersion, xcguideVersion)
        setupCheckboxListener(air3managerCheckbox, air3managerPackageName, air3managerName, air3managerServerVersion, air3managerVersion)
        Timber.d("showUI: end")
    }
    private fun handleRefreshButtonClick() {
        lifecycleScope.launch {
            noInternetAgreed = false // Reset noInternetAgreed
            val isWifiOnly = dataStoreManager.getWifiOnly().firstOrNull() ?: false
            val isNetworkOk = NetworkUtils.checkNetworkAndContinue(
                this@MainActivity,
                isWifiOnly,
                retryAction = {
                    handleRefreshButtonClick()
                },
                listener = this@MainActivity
            )
            if (!isNetworkOk) {
                Timber.d("handleRefreshButtonClick: No network available")
                return@launch
            }
            continueSetup()
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Timber.d("onSaveInstanceState: called")
    }
    override fun onResume() {
        super.onResume()
        Timber.d("onResume: called")
        setActionBarTitleWithSelectedModel()
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
        lifecycleScope.launch {
            getLatestVersionFromServer()
        }
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
    internal suspend fun getLatestVersionFromServer(): Boolean {
        Log.d("MainActivity", "getLatestVersionFromServer() called")
        val selectedModel: String? = dataStoreManager.getSelectedModel().firstOrNull()
        val finalSelectedModel = when {
            selectedModel == null -> getDefaultModel()
            dataStoreManager.isDeviceModelSupported(selectedModel, getSettingsAllowedModels()) -> selectedModel
            else -> getDefaultModel()
        }
        Log.d("MainActivity", "Selected model: $finalSelectedModel")

        var retryCount = 0
        val maxRetries = 3
        var success = false

        while (retryCount < maxRetries && !success) {
            try {
                Log.d("MainActivity", "Fetching latest version from server... (Attempt ${retryCount + 1})")
                val newAppInfos = withContext(Dispatchers.IO) {
                    versionChecker.getLatestVersionFromServer(finalSelectedModel)
                }
                if (newAppInfos.isEmpty()) {
                    Log.e("MainActivity", "getLatestVersionFromServer: Server returned an empty list (Attempt ${retryCount + 1})")
                    retryCount++
                    if (retryCount < maxRetries) {
                        delay(2000) // Wait for 2 seconds before retrying
                    }
                } else {
                    appInfos = newAppInfos
                    Log.d("MainActivity", "Successfully fetched ${appInfos.size} app infos from server")
                    for (appInfo in appInfos) {
                        Log.d("MainActivity", "AppInfo: ${appInfo.name}, Package: ${appInfo.`package`}, APK Path: ${appInfo.apkPath}, Highest Server Version: ${appInfo.highestServerVersion}")
                    }
                    success = true
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error getting latest version from server: ${e.message}")
                // Handle error, e.g., show a toast message to the user
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error getting latest version", Toast.LENGTH_SHORT).show()
                }
                return false
            }
        }
        if (!success) {
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "Error getting latest version after multiple retries", Toast.LENGTH_SHORT).show()
            }
            return false
        }
        return true
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
            val isWifiOnly = dataStoreManager.getWifiOnly().firstOrNull() ?: false
            val isNetworkOk = NetworkUtils.checkNetworkAndContinue(this@MainActivity, isWifiOnly, retryAction = {
                handleUpgradeButtonClick()
            }, listener = this@MainActivity)
            if (!isNetworkOk) {
                Timber.d("handleUpgradeButtonClick: No network available")
                return@launch
            }
            // Request storage permission before proceeding
            permissionsManager.requestStoragePermission {
                // Fetch the latest app information FIRST
                lifecycleScope.launch {
                    getLatestVersionFromServer()
                }.invokeOnCompletion {
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
                    val downloadCompleteReceiver = DownloadCompleteReceiver.getInstance(this@MainActivity)
                    appsToUpgrade.forEach { appInfo ->
                        downloadCompleteReceiver.enqueueDownload(appInfo)
                    }
                    // Start all downloads
                    if (appsToUpgrade.isNotEmpty()) {
                        downloadCompleteReceiver.downloadNextApp(this@MainActivity)
                    }
                }
            }
        }
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
        Timber.d("onPause: called")
    }
    override fun onStop() {
        super.onStop()
        Timber.d("onStop: called")
    }
}

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
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.ActionBar
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import android.widget.ProgressBar
import androidx.glance.visibility
import kotlinx.coroutines.Job
import android.content.IntentFilter

class MainActivity : AppCompatActivity(), NetworkUtils.NetworkDialogListener {

    companion object {
        private const val SETTINGS_REQUEST_CODE = 1
        private const val MODEL_SELECTION_REQUEST_CODE = 2
        private var instance: MainActivity? = null
        fun getInstance(): MainActivity? {
            return instance
        }
    }
    private var isFirstDownload = false
    lateinit var downloadProgressBar: ProgressBar
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
    private val packageInstalledReceiver = PackageInstalledReceiver()


    private var wakeLock: PowerManager.WakeLock? = null
    private var selectedModel: String = ""
    private var appInfos: List<AppInfo> = emptyList() // Corrected type
    private var downloadIdToAppInfo: MutableMap<Long, AppInfo> = mutableMapOf()
    private var fileName: String = ""
    internal var isInstalling = false
    var noInternetAgreed: Boolean = false
    private var supportActionBar: ActionBar? = null

    // Package names of the apps we want to check
    private val xctrackPackageName = "org.xcontest.XCTrack"
    private val xcguidePackageName = "indysoft.xc_guide"
    private val air3managerPackageName = "com.xc.r3"
    private val versionChecker by lazy { VersionChecker(this) }
    private var onCreateCounter = 0
    private var isFirstLaunch = true
    var isLaunchFromModelSelectionActivity: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("onCreate: called")
        onCreateCounter++
        Timber.d("onCreate: onCreate() called - Count: $onCreateCounter")
        Timber.d("onCreate: savedInstanceState is null: ${savedInstanceState == null}")
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        installSplashScreen()
        super.onCreate(savedInstanceState)
        instance = this // Add this line here
        supportActionBar = getSupportActionBar()
        dataStoreManager = DataStoreManager(this)
        dataStoreManager.initializeDataStore() // Add this line

        // Check if launched from ModelSelectionActivity
        isLaunchFromModelSelectionActivity = intent.getBooleanExtra(
            ModelSelectionActivity.EXTRA_LAUNCH_FROM_MODEL_SELECTION,
            false
        )
        Timber.d("onCreate: isLaunchFromModelSelectionActivity: $isLaunchFromModelSelectionActivity")

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
        lifecycleScope.launch {
            val isManualModelSelected = dataStoreManager.getManualModelSelected().firstOrNull() ?: false
            Timber.d("MainActivity: onCreate - isManualModelSelected: $isManualModelSelected")
            if (!isManualModelSelected) {
                // No manual model selected, launch ModelSelectionActivity
                Timber.d("MainActivity: onCreate - Launching ModelSelectionActivity")
                val intent = Intent(this@MainActivity, ModelSelectionActivity::class.java)
                startActivity(intent)
            } else {
                continueOnCreate(savedInstanceState)
            }
        }
    }
    private fun continueOnCreate(savedInstanceState: Bundle?) {
        lifecycleScope.launch {
            // Restore the state of your variables
            if (savedInstanceState != null) {
                noInternetAgreed = savedInstanceState.getBoolean("noInternetAgreed")
                onCreateCounter = savedInstanceState.getInt("onCreateCounter")
                isFirstLaunch = savedInstanceState.getBoolean("isFirstLaunch")
                selectedModel = savedInstanceState.getString("selectedModel") ?: ""
                val restoredAppInfos = savedInstanceState.getParcelableArrayList<AppInfo>("appInfos")
                if (restoredAppInfos != null) {
                    appInfos = restoredAppInfos.toList()
                }
                fileName = savedInstanceState.getString("fileName") ?: ""
                isInstalling = savedInstanceState.getBoolean("isInstalling")
                downloadIdToAppInfo = savedInstanceState.getSerializable("downloadIdToAppInfo") as? MutableMap<Long, AppInfo> ?: mutableMapOf()
            }

            noInternetAgreed = false
            // Initialize the DataStore
            dataStoreManager.initializeDataStore()
            val isManualLaunchFromIntent =
                intent.action == Intent.ACTION_MAIN && intent.categories?.contains(Intent.CATEGORY_LAUNCHER) == true
            permissionsManager = PermissionsManager(this@MainActivity)
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
            val isFirstLaunchDataStore = dataStoreManager.getIsFirstLaunch().firstOrNull() ?: true
            Timber.d("continueOnCreate: lifecycleScope.launch finished") // Added log
            Timber.d("onCreate: end")
        }
        Timber.d("continueOnCreate: end") // Added log
    }
    override fun onNoInternetAgreed() {
        Timber.d("onNoInternetAgreed: called")
        noInternetAgreed = true
        showUI()
    }
    internal fun continueSetup() {
        Timber.d("continueSetup: called")
        setContentView(R.layout.activity_main)
        var dataUsageWarningJob: Job? = null
        dataUsageWarningJob = lifecycleScope.launch {
            NetworkUtils.shouldShowDataUsageWarning(dataStoreManager).collect { shouldShowWarning ->
                if (shouldShowWarning) {
                    NetworkUtils.showDataUsageWarningDialog(this@MainActivity, dataStoreManager) {
                        // This lambda is called when the user clicks "Accept and Continue"
                        dataUsageWarningJob?.cancel() // Cancel the current collection
                        checkNetworkAndContinueLogic()
                    }
                } else {
                    // If the warning should not be shown, proceed directly to checkNetworkAndContinueLogic
                    dataUsageWarningJob?.cancel() // Cancel the current collection
                    checkNetworkAndContinueLogic()
                }
            }
        }
        Timber.d("continueSetup: end")
    }
    private fun checkNetworkAndContinueLogic() {
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
                if (isManualLaunchFromIntent || (!isManualLaunch && unhiddenLaunchOnReboot) || isLaunchFromCheckPromptActivity || isLaunchFromModelSelectionActivity) {
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
        // Initialize the ProgressBar
        downloadProgressBar = findViewById(R.id.downloadProgressBar)

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
    internal fun hideProgressBar() {
        downloadProgressBar.visibility = View.GONE
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Timber.d("onSaveInstanceState: called")
        // Save the state of your variables
        outState.putBoolean("noInternetAgreed", noInternetAgreed)
        outState.putInt("onCreateCounter", onCreateCounter)
        outState.putBoolean("isFirstLaunch", isFirstLaunch)
        outState.putString("selectedModel", selectedModel)
        outState.putParcelableArrayList("appInfos", ArrayList(appInfos))
        outState.putString("fileName", fileName)
        outState.putBoolean("isInstalling", isInstalling)
        outState.putSerializable("downloadIdToAppInfo", HashMap(downloadIdToAppInfo))


    }
    override fun onResume() {
        super.onResume()
        Timber.d("onResume: called")
        setActionBarTitleWithSelectedModel()
        val intentFilter = IntentFilter(Intent.ACTION_PACKAGE_ADDED)
        intentFilter.addDataScheme("package")
        registerReceiver(packageInstalledReceiver, intentFilter)
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
        } else if (requestCode == MODEL_SELECTION_REQUEST_CODE) {
            // ModelSelectionActivity has finished, continue with the rest of onCreate logic
            continueOnCreate(null)
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
        UiUpdater.setActionBarTitleWithSelectedModel(this, dataStoreManager, lifecycleScope, supportActionBar)
    }
    private fun getDefaultModel(): String {
        return Build.MODEL
    }
    private fun checkAppInstallation() {
        Log.d("MainActivity", "checkAppInstallation() called")
        lifecycleScope.launch {
            val selectedModel: String? = dataStoreManager.getSelectedModel().firstOrNull()
            val finalSelectedModel = selectedModel ?: getDefaultModel()
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
        val finalSelectedModel = selectedModel ?: getDefaultModel()
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
                    appInfos = newAppInfos.map { appInfo ->
                        val filteredVersion = if (appInfo.`package` == air3managerPackageName) {
                            filterVersion(appInfo.highestServerVersion)
                        } else {
                            appInfo.highestServerVersion
                        }
                        appInfo.copy(highestServerVersion = filteredVersion)
                    }
                    Log.d("MainActivity", "Successfully fetched ${appInfos.size} app infos from server")
                    for (appInfo in appInfos) {
                        Log.d("MainActivity", "AppInfo: ${appInfo.name}, Package: ${appInfo.`package`}, APK Path: ${appInfo.apkPath}, Highest Server Version: ${appInfo.highestServerVersion}")
                    }
                    success = true
                }
            } catch (e: CancellationException) {
                Log.d("MainActivity", "getLatestVersionFromServer: Coroutine was cancelled")
                // Ignore the CancellationException, it's expected
                return false
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
    private fun filterVersion(version: String): String {
        val parts = version.split(".")
        return if (parts.size > 2) {
            "${parts[0]}.${parts[1]}"
        } else {
            version
        }
    }
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        Timber.d("onConfigurationChanged: called")
        // Check if the new configuration is landscape
        if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            Timber.d("onConfigurationChanged: Landscape mode")
        } else {
            Timber.d("onConfigurationChanged: Not landscape mode")
            // Optionally, you can add code here to handle non-landscape orientations,
            // such as showing a message to the user or preventing the change.
            // But in our case, we don't want to do anything, we just want to stay in landscape mode.
        }
    }
    private fun handleUpgradeButtonClick() {
        lifecycleScope.launch {
            selectedModel = dataStoreManager.getSelectedModel().firstOrNull() ?: getDefaultModel()
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
                        // Show the "Downloading" Toast message here
                        Toast.makeText(this@MainActivity, getString(R.string.downloading), Toast.LENGTH_SHORT).show()
                        isFirstDownload = true
                        downloadProgressBar.visibility = View.VISIBLE
                        downloadCompleteReceiver.downloadNextApp(this@MainActivity)
                    } else {
                        // Show a message if no apps are selected
                        Toast.makeText(this@MainActivity, getString(R.string.no_apps_selected_for_upgrade), Toast.LENGTH_SHORT).show()
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
        lifecycleScope.launch {
            if (isFirstLaunch && permissionsManager.checkAllPermissionsGranted()) {
                dataStoreManager.saveIsFirstLaunch(false)
            }
        }
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
        unregisterReceiver(packageInstalledReceiver)
    }
    override fun onStop() {
        super.onStop()
        Timber.d("onStop: called")
    }
}
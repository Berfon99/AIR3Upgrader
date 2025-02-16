package com.xc.air3upgrader

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.xc.air3upgrader.R.string
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {

    companion object {
        const val MODEL_CHANGED_RESULT_CODE = 100
    }

    private lateinit var modelSpinner: Spinner
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var deviceName: String
    private var previousSelection: String? = null
    private var isSpinnerInitialized = false
    private var spinnerListener: AdapterView.OnItemSelectedListener? = null
    private var isUserInteracting = false
    private var isModelChanged = false
    private lateinit var upgradeCheckIntervalDaysEditText: EditText
    private lateinit var upgradeCheckIntervalHoursEditText: EditText
    private lateinit var upgradeCheckIntervalMinutesEditText: EditText
    private lateinit var timeRemainingValue: TextView
    private lateinit var setUpgradeCheckIntervalButton: Button
    private var isSettingInterval = false
    private var lastClickTime = 0L
    private val clickDebounceThreshold = 500L // milliseconds
    private val handler = Handler(Looper.getMainLooper())
    private var isButtonClickEnabled = true
    private lateinit var enableBackgroundCheckCheckbox: CheckBox
    private lateinit var wifiOnlyCheckbox: CheckBox
    private lateinit var workManager: WorkManager
    private lateinit var startingTimeValue: TextView
    private var isUpdatingTimeRemaining = false
    private var isSchedulingWorker = false
    private lateinit var automaticUpgradeReminderValue: TextView
    private lateinit var unhiddenLaunchOnRebootValue: TextView
    private lateinit var permissionsManager: PermissionsManager
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>

    // Checkbox listener
    private val checkboxListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        Timber.d("checkboxListener: Starting - isChecked: $isChecked")
        lifecycleScope.launch {
            dataStoreManager.saveAutomaticUpgradeReminder(isChecked)
            updateUiState(isChecked)
            updateFlagsValues()
            if (isChecked) {
                Timber.d("checkboxListener: Inside if (isChecked) - About to show AlertDialog")
                // Check if the permission is already granted
                if (!Settings.canDrawOverlays(this@SettingsActivity)) {
                    // Show an alert dialog to explain the permission
                    val builder = AlertDialog.Builder(this@SettingsActivity)
                    builder.setTitle("Permission Required") // You can change the title
                    builder.setMessage("The 'Display over other apps' permission is necessary to show the upgrade reminder on top of other apps.") // You can change the message
                    builder.setPositiveButton("OK") { dialog, _ ->
                        // User clicked OK, proceed to check and request permission
                        permissionsManager.checkOverlayPermission(this@SettingsActivity, packageName, enableBackgroundCheckCheckbox)
                        dialog.dismiss()
                    }
                    builder.setNegativeButton("Cancel") { dialog, _ ->
                        // User clicked Cancel, do nothing
                        lifecycleScope.launch { // Add this line
                            enableBackgroundCheckCheckbox.isChecked = false
                            dataStoreManager.saveAutomaticUpgradeReminder(false)
                            updateUiState(false)
                            updateFlagsValues()
                        } // Add this line
                        dialog.dismiss()
                    }
                    builder.show()
                }
            }
        }
    }
    // New Checkbox listener
    private val wifiOnlyCheckboxListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        Timber.d("wifiOnlyCheckboxListener: Starting - isChecked: $isChecked")
        lifecycleScope.launch {
            dataStoreManager.saveWifiOnly(isChecked)
        }
    }

    private val allowedModels = listOf(
        "AIR3-7.2",
        "AIR3-7.3",
        "AIR3-7.3+",
        "AIR3-7.35",
        "AIR3-7.35+"
    )

    // List of models for the spinner
    private lateinit var modelList: MutableList<String>
    // List of models for the spinner display
    private lateinit var modelDisplayList: MutableList<String>
    // Map to link display strings to models
    private lateinit var modelDisplayMap: MutableMap<String, String?>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("SettingsActivity: onCreate called")

        setContentView(R.layout.activity_settings)

        dataStoreManager = DataStoreManager(this)
        workManager = WorkManager.getInstance(this)
        permissionsManager = PermissionsManager(this)
        PermissionsManager.getClassName()

        // Initialize UI elements
        modelSpinner = findViewById(R.id.model_spinner)
        upgradeCheckIntervalDaysEditText = findViewById(R.id.upgrade_check_interval_days)
        upgradeCheckIntervalHoursEditText = findViewById(R.id.upgrade_check_interval_hours)
        upgradeCheckIntervalMinutesEditText = findViewById(R.id.upgrade_check_interval_minutes)
        timeRemainingValue = findViewById(R.id.time_remaining_value)
        setUpgradeCheckIntervalButton = findViewById(R.id.set_upgrade_check_interval_button)
        automaticUpgradeReminderValue = findViewById(R.id.automatic_upgrade_reminder_value)
        unhiddenLaunchOnRebootValue = findViewById(R.id.unhidden_launch_on_reboot_value)
        enableBackgroundCheckCheckbox = findViewById(R.id.enable_background_check_checkbox)
        wifiOnlyCheckbox = findViewById(R.id.wifi_only_checkbox)
        startingTimeValue = findViewById(R.id.starting_time_value)

        // Initialize the model list
        modelList = allowedModels.toMutableList()
        deviceName = getDeviceName()
        modelList.add(deviceName)

        // Initialize the display list and map
        modelDisplayList = mutableListOf()
        modelDisplayMap = mutableMapOf()
        for (model in modelList) {
            val displayString = if (model == deviceName) {
                getString(string.device_name) + " " + deviceName
            } else {
                model
            }
            modelDisplayList.add(displayString)
            modelDisplayMap[displayString] = if (model == deviceName) null else model
        }

        // Create an ArrayAdapter for the spinner
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelDisplayList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter

        // Set a listener to respond to user selections
        spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                Timber.d("onItemSelected called")
                val selectedDisplayString = parent.getItemAtPosition(position).toString()
                val selectedModel = modelDisplayMap[selectedDisplayString]
                if (previousSelection != selectedModel) {
                    if (selectedModel == null) {
                        showDeviceNameConfirmationDialog()
                    } else {
                        if (!dataStoreManager.isDeviceModelSupported(selectedModel, getAllowedModels())) {
                            Toast.makeText(this@SettingsActivity, getString(string.error_invalid_file), Toast.LENGTH_SHORT).show()
                            modelSpinner.setSelection(modelList.indexOf(previousSelection))
                            return
                        }
                        saveSelectedModel(selectedModel)
                        isModelChanged = true
                        previousSelection = selectedModel
                    }
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Another interface callback
            }
        }
        modelSpinner.onItemSelectedListener = spinnerListener

        // Set the default selection based on the device model
        lifecycleScope.launch {
            val selectedModel = dataStoreManager.getSelectedModel().firstOrNull()
            val defaultSelection = selectedModel ?: Build.MODEL
            modelSpinner.setSelection(modelList.indexOf(defaultSelection))
            previousSelection = defaultSelection
        }

        overlayPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.d("overlayPermissionLauncher: called")
            lifecycleScope.launch {
                if (Settings.canDrawOverlays(this@SettingsActivity)) {
                    Timber.d("overlayPermissionLauncher: Display over other apps permission granted")
                    dataStoreManager.saveAutomaticUpgradeReminder(true)
                    // Après avoir mis à jour le flag AUTOMATIC_UPGRADE_REMINDER dans la logique de permission
                    dataStoreManager.getAutomaticUpgradeReminder().collect { isEnabled ->
                        // Mettre à jour la CheckBox en fonction de la valeur du flag
                        enableBackgroundCheckCheckbox.isChecked = isEnabled
                    }
                } else {
                    Timber.d("overlayPermissionLauncher: Display over other apps permission not granted")
                    enableBackgroundCheckCheckbox.isChecked = false
                    dataStoreManager.saveAutomaticUpgradeReminder(false)
                }
            }
            Timber.d("overlayPermissionLauncher: end")
        }

        permissionsManager.setupOverlayPermissionLauncher(
            overlayPermissionLauncher,
            {
                lifecycleScope.launch {
                    dataStoreManager.saveAutomaticUpgradeReminder(true)
                    updateUiState(true)
                }
            },
            {
                enableBackgroundCheckCheckbox.isChecked = false
                lifecycleScope.launch {
                    dataStoreManager.saveAutomaticUpgradeReminder(false)
                    updateUiState(false)
                }
            },
            lifecycleScope
        )

        // Load and set the saved interval values
        Timber.d("SettingsActivity: onCreate - calling loadUpgradeCheckInterval")
        loadUpgradeCheckInterval()

        // Set the click listener for the button
        setUpgradeCheckIntervalButton.setOnClickListener {
            Timber.d("SettingsActivity: setUpgradeCheckIntervalButton onClick listener called")
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClickTime > clickDebounceThreshold && isButtonClickEnabled) {
                isButtonClickEnabled = false
                lastClickTime = currentTime
                Timber.d("SettingsActivity: setUpgradeCheckIntervalButton onClick called")
                setUpgradeCheckInterval()
                handler.postDelayed({ isButtonClickEnabled = true }, clickDebounceThreshold)
            } else {
                Timber.d("SettingsActivity: setUpgradeCheckIntervalButton onClick ignored")
            }
        }

        lifecycleScope.launch {
            val shouldLaunch = dataStoreManager.getUnhiddenLaunchOnReboot().firstOrNull()

            if (shouldLaunch == null) {
                dataStoreManager.saveUnhiddenLaunchOnReboot(false)
            }
        }

        // Load the initial state from DataStore and update the UI
        lifecycleScope.launch {
            val isEnabled = dataStoreManager.getAutomaticUpgradeReminder().firstOrNull() ?: false
            // Set the checkbox state based on the DataStore value
            enableBackgroundCheckCheckbox.isChecked = isEnabled
            // Update UI values
            updateFlagsValues()
            // Update UI state
            updateUiState(isEnabled) // Add this line
            // Attach the listener here, after updateUiState()
            enableBackgroundCheckCheckbox.setOnCheckedChangeListener(checkboxListener) // Move this line here
        }
        // Load the initial state of the Wi-Fi Only checkbox
        lifecycleScope.launch {
            val isWifiOnly = dataStoreManager.getWifiOnly().firstOrNull() ?: false
            wifiOnlyCheckbox.isChecked = isWifiOnly
        }

        // Attach the listener to the Wi-Fi Only checkbox
        wifiOnlyCheckbox.setOnCheckedChangeListener(wifiOnlyCheckboxListener)

        Timber.d("SettingsActivity: onCreate - END")
    }
    private fun updateFlagsValues() {
        Timber.d("updateFlagsValues: Starting")
        lifecycleScope.launch {
            val isAutomaticUpgradeReminderEnabled = dataStoreManager.getAutomaticUpgradeReminder().firstOrNull() ?: false
            val isUnhiddenLaunchOnRebootEnabled = dataStoreManager.getUnhiddenLaunchOnReboot().firstOrNull() ?: false
            Timber.d("SettingsActivity: updateFlagsValues - isAutomaticUpgradeReminderEnabled: $isAutomaticUpgradeReminderEnabled")
            Timber.d("SettingsActivity: updateFlagsValues - isUnhiddenLaunchOnRebootEnabled: $isUnhiddenLaunchOnRebootEnabled")
            automaticUpgradeReminderValue.text = "Automatic Upgrade Reminder: $isAutomaticUpgradeReminderEnabled"
            unhiddenLaunchOnRebootValue.text = "Unhidden Launch On Reboot: $isUnhiddenLaunchOnRebootEnabled"
            enableBackgroundCheckCheckbox.isChecked = isAutomaticUpgradeReminderEnabled
            if (!isAutomaticUpgradeReminderEnabled) {
                startingTimeValue.text = getString(R.string.not_set)
                dataStoreManager.saveUnhiddenLaunchOnReboot(false) // Add this line
                dataStoreManager.removeLastCheckTime()
                handler.removeCallbacks(updateTimeRemainingRunnable)
            }
        }
    }
    private val updateTimeRemainingRunnable = object : Runnable {
        override fun run() {
            lifecycleScope.launch {
                val isEnabled = enableBackgroundCheckCheckbox.isChecked
                if (isEnabled) {
                    updateTimeRemaining()
                }
            }
            handler.postDelayed(this, 1000) // Update every 1 second
        }
    }
    private fun updateUiState(isEnabled: Boolean) {
        Timber.d("updateUiState: Starting - isEnabled: $isEnabled")
        upgradeCheckIntervalDaysEditText.isEnabled = isEnabled
        upgradeCheckIntervalHoursEditText.isEnabled = isEnabled
        upgradeCheckIntervalMinutesEditText.isEnabled = isEnabled
        setUpgradeCheckIntervalButton.isEnabled = isEnabled
        if (!isEnabled) {
            timeRemainingValue.text = getString(R.string.disabled)
        }
        Timber.d("SettingsActivity: updateUiState - END")
    }
    private fun updateTimeRemaining() {
        if (isUpdatingTimeRemaining) {
            return
        }
        isUpdatingTimeRemaining = true
        Timber.d("SettingsActivity: updateTimeRemaining called")
        lifecycleScope.launch {
            val isEnabled = dataStoreManager.getAutomaticUpgradeReminder().firstOrNull() ?: false
            if (!isEnabled) {
                timeRemainingValue.text = getString(R.string.disabled)
                isUpdatingTimeRemaining = false
                return@launch
            }
            val interval = dataStoreManager.getUpgradeCheckInterval().firstOrNull() ?: Interval(0, 0, 0)
            var lastCheckTime = dataStoreManager.getLastCheckTime().firstOrNull() ?: 0L
            val currentTime = Calendar.getInstance().timeInMillis

            Timber.d("SettingsActivity: updateTimeRemaining - interval: $interval")
            Timber.d("SettingsActivity: updateTimeRemaining - lastCheckTime: $lastCheckTime")
            Timber.d("SettingsActivity: updateTimeRemaining - currentTime: $currentTime")

            val intervalMillis = (interval.days * 24 * 60 * 60 * 1000L) + (interval.hours * 60 * 60 * 1000L) + (interval.minutes * 60 * 1000L)
            val timeElapsed = currentTime - lastCheckTime
            var timeRemaining = intervalMillis - timeElapsed

            Timber.d("SettingsActivity: updateTimeRemaining - intervalMillis: $intervalMillis")
            Timber.d("SettingsActivity: updateTimeRemaining - timeElapsed: $timeElapsed")
            Timber.d("SettingsActivity: updateTimeRemaining - timeRemaining (before check): $timeRemaining")

            // Check if timeRemaining is equal to 0
            if (timeRemaining == 0L) {
                Timber.d("SettingsActivity: updateTimeRemaining - timeRemaining is equal to 0")
                lastCheckTime = currentTime
                dataStoreManager.saveLastCheckTime(lastCheckTime)
                Timber.d("SettingsActivity: updateTimeRemaining - lastCheckTime saved: $lastCheckTime")
                scheduleUpgradeCheckWorker(interval.days, interval.hours, interval.minutes)
                dataStoreManager.saveUnhiddenLaunchOnReboot(true)
            }
            // Check if timeRemaining is negative
            if (timeRemaining < 0) {
                Timber.d("SettingsActivity: updateTimeRemaining - timeRemaining is negative")
                lastCheckTime = currentTime
                dataStoreManager.saveLastCheckTime(lastCheckTime)
                Timber.d("SettingsActivity: updateTimeRemaining - lastCheckTime saved: $lastCheckTime")
                timeRemaining = intervalMillis // Set timeRemaining to intervalMillis
                Timber.d("SettingsActivity: updateTimeRemaining - timeRemaining (after negative check): $timeRemaining")
                scheduleUpgradeCheckWorker(interval.days, interval.hours, interval.minutes)
                dataStoreManager.saveUnhiddenLaunchOnReboot(true)
            }
            // Check if timeRemaining is close to 0
            if (timeRemaining in 1..999) {
                Timber.d("SettingsActivity: updateTimeRemaining - timeRemaining is close to 0")
                dataStoreManager.saveUnhiddenLaunchOnReboot(true)
            }
            timeRemainingValue.text = formatTimeRemaining(timeRemaining)
            Timber.d("SettingsActivity: updateTimeRemaining - timeRemainingValue.text: ${timeRemainingValue.text}")
            Timber.d("SettingsActivity: updateTimeRemaining - END")
            updateFlagsValues() // Add this line
            isUpdatingTimeRemaining = false
        }
    }
    private fun updateStartingTime() {
        Timber.d("SettingsActivity: updateStartingTime called")
        lifecycleScope.launch {
            val lastCheckTime = dataStoreManager.getLastCheckTime().firstOrNull() ?: 0L
            Timber.d("SettingsActivity: updateStartingTime - lastCheckTime: $lastCheckTime")
            if (lastCheckTime != 0L) {
                val formattedTime = DateFormat.format("yyyy-MM-dd HH:mm:ss", lastCheckTime).toString()
                startingTimeValue.text = formattedTime
            } else {
                startingTimeValue.text = getString(R.string.not_set)
            }
        }
    }
    override fun onResume() {
        super.onResume()
        Timber.d("SettingsActivity: onResume called")
        updateFlagsValues()
        updateStartingTime()
        handler.post(updateTimeRemainingRunnable)
    }
    override fun onPause() {
        super.onPause()
        Timber.d("SettingsActivity: onPause called")
        handler.removeCallbacks(updateTimeRemainingRunnable)
    }
    private fun loadUpgradeCheckInterval() {
        Timber.d("SettingsActivity: loadUpgradeCheckInterval called")
        lifecycleScope.launch {
            var interval = dataStoreManager.getUpgradeCheckInterval().firstOrNull()
            Timber.d("SettingsActivity: loadUpgradeCheckInterval - interval before check: $interval")
            if (interval == null) {
                // If interval is null, set a default of 1 minute
                Timber.d("SettingsActivity: loadUpgradeCheckInterval - interval is null, creating default")
                interval = Interval(0, 0, 1)
                dataStoreManager.saveUpgradeCheckInterval(interval)
            }
            Timber.d("SettingsActivity: loadUpgradeCheckInterval - interval.days: ${interval.days}, interval.hours: ${interval.hours}, interval.minutes: ${interval.minutes}")
            // Set the EditText values, even if they were just set to default
            upgradeCheckIntervalDaysEditText.setText(String.format(Locale.getDefault(), "%d", interval.days))
            upgradeCheckIntervalHoursEditText.setText(String.format(Locale.getDefault(), "%d", interval.hours))
            upgradeCheckIntervalMinutesEditText.setText(String.format(Locale.getDefault(), "%d", interval.minutes))
            Timber.d("SettingsActivity: loadUpgradeCheckInterval - END")
        }
        // Start updating the time remaining after the interval is loaded
        handler.post(updateTimeRemainingRunnable)
    }
    private fun setUpgradeCheckInterval() {
        Timber.d("SettingsActivity: setUpgradeCheckInterval called")
        Timber.d("SettingsActivity: setUpgradeCheckInterval - isSettingInterval (before check): $isSettingInterval")
        // Check if the function is already running
        if (isSettingInterval) {
            Timber.d("SettingsActivity: setUpgradeCheckInterval already running, skipping")
            return
        }
        isSettingInterval = true
        val days = upgradeCheckIntervalDaysEditText.text.toString().toIntOrNull() ?: 0
        val hours = upgradeCheckIntervalHoursEditText.text.toString().toIntOrNull() ?: 0
        val minutes = upgradeCheckIntervalMinutesEditText.text.toString().toIntOrNull() ?: 0
        Timber.d("SettingsActivity: setUpgradeCheckInterval - days: $days, hours: $hours, minutes: $minutes")

        lifecycleScope.launch {
            val interval = Interval(days, hours, minutes)
            Timber.d("SettingsActivity: setUpgradeCheckInterval - interval before save: $interval")
            dataStoreManager.saveUpgradeCheckInterval(interval)
            Timber.d("SettingsActivity: setUpgradeCheckInterval - interval saved: $interval")
            // Schedule the UpgradeCheckWorker
            Timber.d("SettingsActivity: setUpgradeCheckInterval - calling scheduleUpgradeCheckWorker")
            scheduleUpgradeCheckWorker(days, hours, minutes)
            // Update the lastCheckTime
            val currentTime = Calendar.getInstance().timeInMillis
            dataStoreManager.saveLastCheckTime(currentTime)
            Timber.d("SettingsActivity: setUpgradeCheckInterval - lastCheckTime saved: $currentTime")
            updateStartingTime() // Add this line
            handler.post(updateTimeRemainingRunnable) // Add this line
        }
        isSettingInterval = false
        Timber.d("SettingsActivity: setUpgradeCheckInterval - isSettingInterval (after reset): $isSettingInterval")
        Timber.d("SettingsActivity: setUpgradeCheckInterval - END")
    }
    private fun scheduleUpgradeCheckWorker(days: Int, hours: Int, minutes: Int) {
        if (isSchedulingWorker) {
            return
        }
        isSchedulingWorker = true
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker called")
        val initialDelay = (days * 24 * 60) + (hours * 60) + minutes
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker - initialDelayDays: $days")
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker - initialDelayHours: $hours")
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker - initialDelayMinutes: $minutes")
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker - initialDelay: $initialDelay")
        val intervalMillis = (days * 24 * 60 * 60 * 1000L) + (hours * 60 * 60 * 1000L) + (minutes * 60 * 1000L)
        val intervalMinutes = intervalMillis / (60 * 1000)
        val upgradeCheckRequest = PeriodicWorkRequest.Builder(
            UpgradeCheckWorker::class.java,
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setInitialDelay(initialDelay.toLong(), TimeUnit.MINUTES)
            .build()
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker - before enqueueUniquePeriodicWork")
        WorkManager.getInstance(this).cancelUniqueWork("UpgradeCheck")
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UpgradeCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            upgradeCheckRequest
        )
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker - after enqueueUniquePeriodicWork")
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker - END")
        isSchedulingWorker = false
    }
    private fun formatTimeRemaining(timeRemainingMillis: Long): String {
        Timber.d("SettingsActivity: formatTimeRemaining called")
        Timber.d("SettingsActivity: formatTimeRemaining - timeRemainingMillis: $timeRemainingMillis")
        if (timeRemainingMillis < 0) {
            return getString(R.string.less_than_a_second)
        }
        val seconds = timeRemainingMillis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> String.format(Locale.getDefault(), "%d %s", days, if (days == 1L) getString(R.string.day) else getString(R.string.days))
            hours > 0 -> String.format(Locale.getDefault(), "%d %s", hours, if (hours == 1L) getString(R.string.hour) else getString(R.string.hours))
            minutes > 0 -> String.format(Locale.getDefault(), "%d %s", minutes, if (minutes == 1L) getString(R.string.minute) else getString(R.string.minutes))
            seconds > 0 -> String.format(Locale.getDefault(), "%d %s", seconds, if (seconds == 1L) getString(R.string.second) else getString(R.string.seconds))
            else -> getString(R.string.less_than_a_second)
        }
    }
    private fun showDeviceNameConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.device_name_confirmation_title))
        builder.setMessage(getString(R.string.device_name_confirmation_message))
        builder.setPositiveButton(getString(R.string.yes)) { dialog, _ ->
            // Save the device name as the selected model
            saveSelectedModel(null)
            isModelChanged = true
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.no)) { dialog, _ ->
            // Reset the selection to the previous one
            modelSpinner.setSelection(modelList.indexOf(previousSelection))
            dialog.dismiss()
        }
        builder.show()
    }
    private fun saveSelectedModel(selectedModel: String?) {
        lifecycleScope.launch {
            dataStoreManager.saveSelectedModel(selectedModel ?: deviceName)
        }
    }
    private fun getDeviceName(): String {
        return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            ?: getString(R.string.unknown_device) // Use string resource
    }
    internal fun getAllowedModels(): List<String> {
        return allowedModels
    }
}
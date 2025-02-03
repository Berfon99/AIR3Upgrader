package com.xc.air3upgrader

import android.os.Build
import android.text.format.DateFormat
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xc.air3upgrader.R.string.error_invalid_file
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Calendar
import android.widget.TextView
import android.widget.Button
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.os.Handler
import android.os.Looper
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.compose.foundation.layout.add
import androidx.compose.ui.semantics.text
import androidx.work.NetworkType
import kotlinx.coroutines.runBlocking

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
    private lateinit var workManager: WorkManager
    private lateinit var startingTimeValue: TextView
    private lateinit var shouldLaunchOnRebootValue: TextView
    private var isUpdatingTimeRemaining = false
    private var isSchedulingWorker = false

// --- End of Part 1 ---





    private fun updateTimeRemaining() {
        if (isUpdatingTimeRemaining) {
            return
        }
        isUpdatingTimeRemaining = true
        Timber.d("SettingsActivity: updateTimeRemaining called")
        lifecycleScope.launch {
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

            // Check if timeRemaining is negative or close to 0
            if (timeRemaining <= 0) {
                Timber.d("SettingsActivity: updateTimeRemaining - timeRemaining is negative or close to 0")
                dataStoreManager.saveUnhiddenLaunchOnReboot(true) // Set unhiddenLaunchOnReboot to true
                Timber.d("SettingsActivity: updateTimeRemaining - unhiddenLaunchOnReboot saved: true")
                updateUnhiddenLaunchOnRebootValue()
                updateStartingTime()
                timeRemainingValue.text = formatTimeRemaining(0)
                Timber.d("SettingsActivity: updateTimeRemaining - timeRemainingValue.text: ${timeRemainingValue.text}")
                Timber.d("SettingsActivity: updateTimeRemaining - END")
                isUpdatingTimeRemaining = false
                return@launch
            }
            timeRemainingValue.text = formatTimeRemaining(timeRemaining)
            Timber.d("SettingsActivity: updateTimeRemaining - timeRemainingValue.text: ${timeRemainingValue.text}")
            Timber.d("SettingsActivity: updateTimeRemaining - END")
            isUpdatingTimeRemaining = false
        }
    }

    private fun updateShouldLaunchOnRebootValue() {
        Timber.d("SettingsActivity: updateShouldLaunchOnRebootValue called")
        lifecycleScope.launch {
            val shouldLaunch = dataStoreManager.getShouldLaunchOnReboot().firstOrNull() ?: false
            Timber.d("SettingsActivity: updateShouldLaunchOnRebootValue - shouldLaunch: $shouldLaunch")
            val shouldLaunchString = if (shouldLaunch) "true" else "false"
            shouldLaunchOnRebootValue.text = shouldLaunchString
            Timber.d("SettingsActivity: updateShouldLaunchOnRebootValue - END")
        }
    }

    private fun updateUnhiddenLaunchOnRebootValue() {
        Timber.d("SettingsActivity: updateUnhiddenLaunchOnRebootValue called")
        lifecycleScope.launch {
            val unhiddenLaunch = dataStoreManager.getUnhiddenLaunchOnReboot().firstOrNull() ?: false
            Timber.d("SettingsActivity: updateUnhiddenLaunchOnRebootValue - unhiddenLaunch: $unhiddenLaunch")
            val unhiddenLaunchString = if (unhiddenLaunch) "true" else "false"
            unhiddenLaunchOnRebootValue.text = unhiddenLaunchString
            Timber.d("SettingsActivity: updateUnhiddenLaunchOnRebootValue - END")
        }
    }

    private fun updateStartingTime() {
        Timber.d("SettingsActivity: updateStartingTime called")
        lifecycleScope.launch {
            val lastCheckTime = dataStoreManager.getLastCheckTime().firstOrNull() ?: 0L
            Timber.d("SettingsActivity: updateStartingTime - lastCheckTime: $lastCheckTime")
            if (lastCheckTime != 0L) {
                val formattedTime = DateFormat.format("yyyy-MM-dd HH:mm:ss", lastCheckTime).toString()
                Timber.d("SettingsActivity: updateStartingTime - formattedTime: $formattedTime")
                startingTimeValue.text = formattedTime
            } else {
                startingTimeValue.text = getString(R.string.not_available)
            }
            Timber.d("SettingsActivity: updateStartingTime - END")
        }
    }

// --- End of Part 2 ---
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    Timber.d("SettingsActivity: onCreate called")
    setContentView(R.layout.activity_settings)

    dataStoreManager = DataStoreManager(this)
    workManager = WorkManager.getInstance(this)

    modelSpinner = findViewById(R.id.model_spinner)
    upgradeCheckIntervalDaysEditText = findViewById(R.id.upgrade_check_interval_days)
    upgradeCheckIntervalHoursEditText = findViewById(R.id.upgrade_check_interval_hours)
    upgradeCheckIntervalMinutesEditText = findViewById(R.id.upgrade_check_interval_minutes)
    timeRemainingValue = findViewById(R.id.time_remaining_value)
    setUpgradeCheckIntervalButton = findViewById(R.id.set_upgrade_check_interval_button)
    enableBackgroundCheckCheckbox = findViewById(R.id.enable_background_check_checkbox)
    startingTimeValue = findViewById(R.id.starting_time_value)
    shouldLaunchOnRebootValue = findViewById(R.id.should_launch_on_reboot_value)
    unhiddenLaunchOnRebootValue = findViewById(R.id.UNHIDDEN_LAUNCH_ON_REBOOT)

    // Initialize the model list
    modelList = allowedModels.toMutableList()
    deviceName = getDeviceName()
    modelList.add(deviceName)

    // Initialize the display list and map
    modelDisplayList = mutableListOf()
    modelDisplayMap = mutableMapOf()
    for (model in modelList) {
        val displayString = if (model == deviceName) {
            getString(R.string.device_name) + " " + deviceName // Use string resource
        } else {
            model
        }
        modelDisplayList.add(displayString)
        modelDisplayMap[displayString] = if (model == deviceName) null else model
    }

    // Create an ArrayAdapter using the string array and a default spinner layout
    val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelDisplayList)
    // Specify the layout to use when the list of choices appears
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    // Apply the adapter to the spinner
    modelSpinner.adapter = adapter

    // Set the default selection based on the device model
    lifecycleScope.launch {
        val selectedModel = dataStoreManager.getSelectedModel().firstOrNull()
        val defaultSelection = when {
            selectedModel != null -> selectedModel
            else -> Build.MODEL // Default to device model on fresh install
        }

        // Remove the listener temporarily
        spinnerListener = modelSpinner.onItemSelectedListener
        modelSpinner.onItemSelectedListener = null

        modelSpinner.setSelection(modelList.indexOf(defaultSelection))
        previousSelection = defaultSelection
        isSpinnerInitialized = true

        // Re-add the listener
        modelSpinner.onItemSelectedListener = spinnerListener
    }

    // Set a listener to respond to user selections
    spinnerListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
            Timber.d("onItemSelected called")
            if (!isSpinnerInitialized || !isUserInteracting) {
                // Ignore the initial selection
                return
            }
            val selectedDisplayString = parent.getItemAtPosition(position).toString()
            val selectedModel = modelDisplayMap[selectedDisplayString]

            // Check if the user selected the device name
            if (selectedModel == null) {
                // Show the confirmation dialog
                showDeviceNameConfirmationDialog()
            } else {
                // Validate the selected model
                if (!dataStoreManager.isDeviceModelSupported(selectedModel, getAllowedModels())) {
                    // Display an error message and reset the selection
                    Toast.makeText(this@SettingsActivity, getString(error_invalid_file), Toast.LENGTH_SHORT).show()
                    modelSpinner.setSelection(modelList.indexOf(previousSelection)) // Reset to previous selection
                    return
                }
                // Save the selected model
                saveSelectedModel(selectedModel)
                isModelChanged = true
            }
        }

        override fun onNothingSelected(parent: AdapterView<*>) {
            // Another interface callback
        }
    }
    modelSpinner.onItemSelectedListener = spinnerListener
    // Load and set the saved interval values
    Timber.d("SettingsActivity: onCreate - calling loadUpgradeCheckInterval")
    loadUpgradeCheckInterval()

    lifecycleScope.launch {
        val shouldLaunchOnReboot = dataStoreManager.getShouldLaunchOnReboot().firstOrNull() ?: false
        enableBackgroundCheckCheckbox.isChecked = shouldLaunchOnReboot
        enableBackgroundCheckCheckbox.setOnCheckedChangeListener(checkboxListener)
        dataStoreManager.saveUnhiddenLaunchOnReboot(false)
    }
}

    override fun onResume() {
        super.onResume()
        Timber.d("SettingsActivity: onResume called")
        lifecycleScope.launch {
            val unhiddenLaunchOnReboot = dataStoreManager.getUnhiddenLaunchOnReboot().firstOrNull() ?: false
            Timber.d("SettingsActivity: onResume - unhiddenLaunchOnReboot: $unhiddenLaunchOnReboot")
            if (unhiddenLaunchOnReboot) {
                Timber.d("SettingsActivity: onResume - unhiddenLaunchOnReboot is true, updating lastCheckTime")
                val currentTime = Calendar.getInstance().timeInMillis
                dataStoreManager.saveLastCheckTime(currentTime)
                dataStoreManager.saveUnhiddenLaunchOnReboot(false) // Reset unhiddenLaunchOnReboot
                Timber.d("SettingsActivity: onResume - lastCheckTime updated: $currentTime")
                updateStartingTime()
            }
            val shouldLaunchOnReboot = dataStoreManager.getShouldLaunchOnReboot().firstOrNull() ?: false
            if (shouldLaunchOnReboot) {
                handler.post(updateTimeRemainingRunnable)
            }
        }
        updateShouldLaunchOnRebootValue()
        updateUnhiddenLaunchOnRebootValue()
        updateUiElementsState(enableBackgroundCheckCheckbox.isChecked)
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
            updateUiElementsState(enableBackgroundCheckCheckbox.isChecked)
        }
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
            updateTimeRemaining()
            updateUiElementsState(enableBackgroundCheckCheckbox.isChecked)
            isSettingInterval = false
        }
    }
    private fun showDeviceNameConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.device_name_confirmation_title))
        builder.setMessage(getString(R.string.device_name_confirmation_message))
        builder.setPositiveButton(getString(R.string.yes)) { _, _ ->
            // Save null to indicate device name selected
            saveSelectedModel(null)
            isModelChanged = true
        }
        builder.setNegativeButton(getString(R.string.no)) { dialog, _ ->
            // Reset to previous selection
            modelSpinner.setSelection(modelList.indexOf(previousSelection))
            dialog.dismiss()
        }
        builder.show()
    }

    private fun saveSelectedModel(model: String?) {
        Timber.d("SettingsActivity: saveSelectedModel called")
        lifecycleScope.launch {
            if (model != null) {
                dataStoreManager.saveSelectedModel(model)
            } else {
                dataStoreManager.saveSelectedModel(deviceName)
            }
        }
    }

    private fun getDeviceName(): String {
        return Build.MODEL
    }

    private fun getAllowedModels(): List<String> {
        return allowedModels
    }

    private fun formatTimeRemaining(timeRemaining: Long): String {
        val seconds = timeRemaining / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> String.format(Locale.getDefault(), "%d day%s, %d hour%s, %d minute%s", days, if (days > 1) "s" else "", hours % 24, if (hours % 24 > 1) "s" else "", minutes % 60, if (minutes % 60 > 1) "s" else "")
            hours > 0 -> String.format(Locale.getDefault(), "%d hour%s, %d minute%s", hours, if (hours > 1) "s" else "", minutes % 60, if (minutes % 60 > 1) "s" else "")
            minutes > 0 -> String.format(Locale.getDefault(), "%d minute%s", minutes, if (minutes > 1) "s" else "")
            seconds > 0 -> String.format(Locale.getDefault(), "%d second%s", seconds, if (seconds > 1) "s" else "")
            else -> getString(R.string.less_than_a_second)
        }
    }

    private fun scheduleUpgradeCheck() {
        if (isSchedulingWorker) {
            return
        }
        isSchedulingWorker = true
        Timber.d("SettingsActivity: scheduleUpgradeCheck called")
        lifecycleScope.launch {
            val interval = dataStoreManager.getUpgradeCheckInterval().firstOrNull() ?: Interval(0, 0, 0)
            val intervalMillis = (interval.days * 24 * 60 * 60) + (interval.hours * 60 * 60) + (interval.minutes * 60)
            val periodicWorkRequest = PeriodicWorkRequest.Builder(UpgradeCheckWorker::class.java, intervalMillis.toLong(), TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniquePeriodicWork("UpgradeCheck", ExistingPeriodicWorkPolicy.REPLACE, periodicWorkRequest)
            isSchedulingWorker = false
        }
    }

    private fun cancelUpgradeCheck() {
        Timber.d("SettingsActivity: cancelUpgradeCheck called")
        workManager.cancelUniqueWork("UpgradeCheck")
    }

    private fun updateUiElementsState(isChecked: Boolean) {
        Timber.d("SettingsActivity: updateUiElementsState called - isChecked: $isChecked")
        upgradeCheckIntervalDaysEditText.isEnabled = isChecked
        upgradeCheckIntervalHoursEditText.isEnabled = isChecked
        upgradeCheckIntervalMinutesEditText.isEnabled = isChecked
        setUpgradeCheckIntervalButton.isEnabled = isChecked
        if (!isChecked) {
            upgradeCheckIntervalDaysEditText.setText("")
            upgradeCheckIntervalHoursEditText.setText("")
            upgradeCheckIntervalMinutesEditText.setText("")
        }
        Timber.d("SettingsActivity: updateUiElementsState - END")
    }
}
// --- End of Part 3 ---

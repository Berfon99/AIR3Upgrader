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
    private lateinit var automaticUpgradeReminderValue: TextView
    private lateinit var unhiddenLaunchOnRebootValue: TextView

// Checkbox listener
    private val checkboxListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        Timber.d("SettingsActivity: checkboxListener - onCheckedChanged called")
        Timber.d("SettingsActivity: checkboxListener - isChecked: $isChecked")
        lifecycleScope.launch {
            dataStoreManager.saveAutomaticUpgradeReminder(isChecked)
            updateFlagsValues()
            updateUiState(isChecked)
        }
    }
    // List of allowed models
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
                getString(R.string.device_name) + " " + deviceName
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

        // Set the default selection based on the device model
        lifecycleScope.launch {
            val selectedModel = dataStoreManager.getSelectedModel().firstOrNull()
            val defaultSelection = selectedModel ?: Build.MODEL

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
                    return
                }
                val selectedDisplayString = parent.getItemAtPosition(position).toString()
                val selectedModel = modelDisplayMap[selectedDisplayString]

                if (selectedModel == null) {
                    showDeviceNameConfirmationDialog()
                } else {
                    if (!dataStoreManager.isDeviceModelSupported(selectedModel, getAllowedModels())) {
                        Toast.makeText(this@SettingsActivity, getString(R.string.error_invalid_file), Toast.LENGTH_SHORT).show()
                        modelSpinner.setSelection(modelList.indexOf(previousSelection))
                        return
                    }
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

        // Checkbox listener
        enableBackgroundCheckCheckbox.setOnCheckedChangeListener(checkboxListener)

        lifecycleScope.launch {
            val shouldLaunch = dataStoreManager.getUnhiddenLaunchOnReboot().firstOrNull()

            if (shouldLaunch == null) {
                dataStoreManager.saveUnhiddenLaunchOnReboot(false)
            }
        }

        // Initialize UI values
        updateFlagsValues()
        lifecycleScope.launch {
            val isEnabled = dataStoreManager.getAutomaticUpgradeReminder().firstOrNull() ?: false
            updateUiState(isEnabled)
        }
        Timber.d("SettingsActivity: onCreate - END")
    }
    private fun updateFlagsValues() {
        Timber.d("SettingsActivity: updateFlagsValues called")
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
                dataStoreManager.saveUnhiddenLaunchOnReboot(false)
                dataStoreManager.removeLastCheckTime()
                handler.removeCallbacks(updateTimeRemainingRunnable) // Add this line
            }
            updateStartingTime()
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
        Timber.d("SettingsActivity: updateUiState called")
        Timber.d("SettingsActivity: updateUiState - isEnabled: $isEnabled")
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
        handler.post(updateTimeRemainingRunnable)
        lifecycleScope.launch {
            val isEnabled = dataStoreManager.getAutomaticUpgradeReminder().firstOrNull() ?: false
            updateUiState(isEnabled)
        }
        updateFlagsValues()
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
        return Build.MODEL
    }

    internal fun getAllowedModels(): List<String> {
        return allowedModels
    }
}
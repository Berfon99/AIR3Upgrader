package com.xc.air3upgrader

import android.os.Build
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
    private val updateTimeRemainingRunnable = object : Runnable {
        override fun run() {
            updateTimeRemaining()
            handler.postDelayed(this, 1000) // Update every 1 second
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

        modelSpinner = findViewById(R.id.model_spinner)
        upgradeCheckIntervalDaysEditText = findViewById(R.id.upgrade_check_interval_days)
        upgradeCheckIntervalHoursEditText = findViewById(R.id.upgrade_check_interval_hours)
        upgradeCheckIntervalMinutesEditText = findViewById(R.id.upgrade_check_interval_minutes)
        timeRemainingValue = findViewById(R.id.time_remaining_value)
        setUpgradeCheckIntervalButton = findViewById(R.id.set_upgrade_check_interval_button)

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
        loadUpgradeCheckInterval()
        // Set the click listener for the button
        setUpgradeCheckIntervalButton.setOnClickListener {
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
    }

    private fun loadUpgradeCheckInterval() {
        lifecycleScope.launch {
            val interval = dataStoreManager.getUpgradeCheckInterval().firstOrNull()
            if (interval != null) {
                upgradeCheckIntervalDaysEditText.setText(String.format(Locale.getDefault(), "%d", interval.days))
                upgradeCheckIntervalHoursEditText.setText(String.format(Locale.getDefault(), "%d", interval.hours))
                upgradeCheckIntervalMinutesEditText.setText(String.format(Locale.getDefault(), "%d", interval.minutes))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("SettingsActivity: onResume called")
        lifecycleScope.launch {
            val lastCheckTime = dataStoreManager.getLastCheckTime().firstOrNull() ?: 0L
            Timber.d("SettingsActivity: onResume - lastCheckTime (before check): $lastCheckTime")
            if (lastCheckTime == 0L) {
                Timber.d("SettingsActivity: onResume - lastCheckTime is 0, updating it")
                dataStoreManager.saveLastCheckTime(Calendar.getInstance().timeInMillis)
                val lastCheckTimeUpdated = dataStoreManager.getLastCheckTime().firstOrNull() ?: 0L
                Timber.d("SettingsActivity: onResume - lastCheckTime (after update): $lastCheckTimeUpdated")
            }
        }
        handler.post(updateTimeRemainingRunnable)
    }

    override fun onPause() {
        super.onPause()
        Timber.d("SettingsActivity: onPause called")
        handler.removeCallbacks(updateTimeRemainingRunnable)
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
            dataStoreManager.saveUpgradeCheckInterval(Interval(days, hours, minutes))
            // Schedule the UpgradeCheckWorker
            scheduleUpgradeCheckWorker(days, hours, minutes)
            // Update the lastCheckTime
            val currentTime = Calendar.getInstance().timeInMillis
            dataStoreManager.saveLastCheckTime(currentTime)
            // Call updateTimeRemaining() after saving the new values
            updateTimeRemaining()
        }
        isSettingInterval = false
        Timber.d("SettingsActivity: setUpgradeCheckInterval - isSettingInterval (after reset): $isSettingInterval")
    }

    private fun updateTimeRemaining() {
        Timber.d("SettingsActivity: updateTimeRemaining called")
        lifecycleScope.launch {
            val interval = dataStoreManager.getUpgradeCheckInterval().firstOrNull() ?: Interval(0, 0, 0)
            var lastCheckTime = dataStoreManager.getLastCheckTime().firstOrNull() ?: 0L
            val currentTime = Calendar.getInstance().timeInMillis

            Timber.d("SettingsActivity: updateTimeRemaining - interval: $interval")
            Timber.d("SettingsActivity: updateTimeRemaining - lastCheckTime: $lastCheckTime")
            Timber.d("SettingsActivity: updateTimeRemaining - currentTime: $currentTime")

            val intervalMillis = (interval.days * 24 * 60 * 60 * 1000L) + (interval.hours * 60 * 60 * 1000L) + (interval.minutes * 60 * 1000L)
            var timeElapsed = currentTime - lastCheckTime
            var timeRemaining = intervalMillis - timeElapsed

            Timber.d("SettingsActivity: updateTimeRemaining - intervalMillis: $intervalMillis")
            Timber.d("SettingsActivity: updateTimeRemaining - timeElapsed: $timeElapsed")
            Timber.d("SettingsActivity: updateTimeRemaining - timeRemaining (before check): $timeRemaining")

            // Check if timeRemaining is negative
            if (timeRemaining < 0) {
                timeRemaining = intervalMillis
                Timber.d("SettingsActivity: updateTimeRemaining - timeRemaining (after negative check): $timeRemaining")
                lastCheckTime = currentTime
            }
            // Check if timeRemaining is close to 0
            if (timeRemaining > 0 && timeRemaining < 1000) {
                timeRemaining = 0
                Timber.d("SettingsActivity: updateTimeRemaining - timeRemaining (after close to 0 check): $timeRemaining")
            }
            // Update the lastCheckTime
            dataStoreManager.saveLastCheckTime(lastCheckTime)
            timeRemainingValue.text = formatTimeRemaining(timeRemaining)
            Timber.d("SettingsActivity: updateTimeRemaining - timeRemainingValue.text: ${timeRemainingValue.text}")
        }
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

    private fun scheduleUpgradeCheckWorker(days: Int, hours: Int, minutes: Int) {
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker called")
        val initialDelayMinutes = (days * 24 * 60) + (hours * 60) + minutes
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker - initialDelayMinutes: $initialDelayMinutes")
        val upgradeCheckRequest = PeriodicWorkRequest.Builder(UpgradeCheckWorker::class.java, 1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes.toLong(), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UpgradeCheck",
            ExistingPeriodicWorkPolicy.REPLACE,
            upgradeCheckRequest
        )
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




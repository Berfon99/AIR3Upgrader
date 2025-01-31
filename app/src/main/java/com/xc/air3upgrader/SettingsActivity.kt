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
            setUpgradeCheckInterval()
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
        updateTimeRemaining()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        isUserInteracting = true
    }

    private fun showDeviceNameConfirmationDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.device_name_confirmation_title))
            .setMessage(getString(R.string.device_name_confirmation_message))
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                // User confirmed, save the device name (null)
                saveSelectedModel(null)
                previousSelection = deviceName
                isModelChanged = true // Set the flag
                dialog.dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                // User canceled, reset the selection
                modelSpinner.setSelection(modelList.indexOf(previousSelection))
                dialog.dismiss()
            }
            .create()
        dialog.show()
    }

    private fun saveSelectedModel(selectedModel: String?) {
        lifecycleScope.launch {
            dataStoreManager.saveSelectedModel(selectedModel)
            previousSelection = selectedModel
        }
    }

    private fun updateTimeRemaining() {
        lifecycleScope.launch {
            val interval = dataStoreManager.getUpgradeCheckInterval().firstOrNull() ?: Interval(0, 0, 0)
            val lastCheckTime = dataStoreManager.getLastCheckTime().firstOrNull() ?: 0L
            val currentTime = Calendar.getInstance().timeInMillis

            val intervalMillis = (interval.days * 24 * 60 * 60 * 1000L) + (interval.hours * 60 * 60 * 1000L) + (interval.minutes * 60 * 1000L)
            val timeElapsed = currentTime - lastCheckTime
            val timeRemaining = intervalMillis - timeElapsed

            timeRemainingValue.text = formatTimeRemaining(timeRemaining)
        }
    }

    private fun formatTimeRemaining(timeRemainingMillis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemainingMillis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeRemainingMillis) % 60

        return when {
            minutes > 0 -> {
                val hours = TimeUnit.MILLISECONDS.toHours(timeRemainingMillis)
                val remainingMinutes = minutes % 60
                if (hours > 0) {
                    getString(R.string.time_remaining_hours_minutes, hours, remainingMinutes)
                } else {
                    getString(R.string.time_remaining_minutes, minutes)
                }
            }
            seconds > 0 -> getString(R.string.time_remaining_seconds, seconds)
            else -> getString(R.string.time_remaining_less_than_a_second)
        }
    }

    internal fun getAllowedModels(): List<String> {
        return allowedModels
    }

    private fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer)) {
            model.uppercase()
        } else {
            "$manufacturer $model".uppercase()
        }
    }

    private fun setUpgradeCheckInterval() {
        val days = upgradeCheckIntervalDaysEditText.text.toString().toIntOrNull() ?: 0
        val hours = upgradeCheckIntervalHoursEditText.text.toString().toIntOrNull() ?: 0
        val minutes = upgradeCheckIntervalMinutesEditText.text.toString().toIntOrNull() ?: 0
        lifecycleScope.launch {
            dataStoreManager.saveUpgradeCheckInterval(Interval(days, hours, minutes))
            // Call updateTimeRemaining() after saving the new values
            updateTimeRemaining()
            // Schedule the UpgradeCheckWorker
            scheduleUpgradeCheckWorker(days, hours, minutes)
        }
    }

    private fun scheduleUpgradeCheckWorker(days: Int, hours: Int, minutes: Int) {
        Timber.d("SettingsActivity: scheduleUpgradeCheckWorker called")
        val initialDelayMinutes = (hours * 60) + minutes
        val periodicWorkRequest = PeriodicWorkRequest.Builder(
            UpgradeCheckWorker::class.java,
            days.toLong(),
            TimeUnit.DAYS
        )
            .setInitialDelay(initialDelayMinutes.toLong(), TimeUnit.MINUTES)
            .addTag("UpgradeCheck")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "UpgradeCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }
}

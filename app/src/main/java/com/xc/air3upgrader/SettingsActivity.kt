package com.xc.air3upgrader

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xc.air3upgrader.R.string.error_invalid_file
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import android.content.Intent
import timber.log.Timber

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
                    if (!dataStoreManager.isDeviceModelSupported(selectedModel, allowedModels)) {
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
            .setCancelable(false) // Prevent dismissing by tapping outside
            .create() // Create the dialog
        dialog.show() // Show the dialog
    }

    private fun saveSelectedModel(selectedModel: String?) {
        lifecycleScope.launch {
            try {
                dataStoreManager.saveSelectedModel(selectedModel)
                previousSelection = selectedModel
            } catch (e: Exception) {
                Timber.e(e, "Error saving selected model")
            }
        }
    }

    fun getAllowedModels(): List<String> {
        return allowedModels
    }

    private fun getDeviceName(): String {
        return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            ?: getString(R.string.unknown_device) // Use string resource
    }

    override fun finish() {
        if (isModelChanged) {
            val returnIntent = Intent()
            setResult(MODEL_CHANGED_RESULT_CODE, returnIntent)
        }
        super.finish()
    }
}
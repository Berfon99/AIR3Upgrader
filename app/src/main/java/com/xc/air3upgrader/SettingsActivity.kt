package com.xc.air3upgrader

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.xc.air3upgrader.R.string.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var modelSpinner: Spinner
    private lateinit var deviceInfoTextView: TextView
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var deviceName: String
    private var previousSelection: String? = null
    private var isSpinnerInitialized = false
    private var spinnerListener: AdapterView.OnItemSelectedListener? = null
    private var isUserInteracting = false

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
        deviceInfoTextView = findViewById(R.id.device_info_text_view)

        // Initialize the model list
        modelList = allowedModels.toMutableList()
        deviceName = getDeviceName()
        modelList.add(deviceName)

        // Initialize the display list and map
        modelDisplayList = mutableListOf()
        modelDisplayMap = mutableMapOf()
        for (model in modelList) {
            val displayString = if (model == deviceName) {
                getString(device_name) + " " + deviceName // Use string resource
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
                Log.d("SettingsActivity", "onItemSelected called")
                if (!isSpinnerInitialized || !isUserInteracting) {
                    // Ignore the initial selection
                    return
                }
                val selectedDisplayString = parent.getItemAtPosition(position).toString()
                val selectedModel = modelDisplayMap[selectedDisplayString]

                // Check if the user selected the device name
                if (selectedModel == null) {
                    // Show the confirmation dialog
                    showDeviceNameConfirmationDialog(selectedDisplayString)
                } else {
                    // Validate the selected model
                    if (!dataStoreManager.isDeviceModelSupported(selectedModel, allowedModels)) {
                        // Display an error message and reset the selection
                        Toast.makeText(this@SettingsActivity, getString(error_invalid_file), Toast.LENGTH_SHORT).show()
                        modelSpinner.setSelection(modelList.indexOf(previousSelection)) // Reset to previous selection
                        return
                    }
                    // Show the information dialog
                    showRefreshDataDialog()
                    // Save the selected model
                    saveSelectedModel(selectedModel)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Another interface callback
            }
        }
        modelSpinner.onItemSelectedListener = spinnerListener
        // Get device information
        getDeviceInfo()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        isUserInteracting = true
    }

    private fun showDeviceNameConfirmationDialog(selectedDisplayString: String) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.device_name_confirmation_title))
            .setMessage(getString(R.string.device_name_confirmation_message))
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                // User confirmed, save the device name (null)
                saveSelectedModel(null)
                previousSelection = deviceName
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

    private fun showRefreshDataDialog() {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.refresh_data_title))
            .setMessage(getString(R.string.refresh_data_message))
            .setPositiveButton(getString(R.string.ok)) { dialog, _ ->
                // User confirmed, save the device name (null)
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
                Log.e("SettingsActivity", "Error saving selected model", e)
            }
        }
    }

    public fun getAllowedModels(): List<String> {
        return allowedModels
    }

    private fun getDeviceName(): String {
        return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            ?: getString(unknown_device) // Use string resource
    }

    private fun getDeviceInfo() {
        val deviceInfo = StringBuilder()
        // Device
        deviceInfo.append(getString(device) + " " + Build.DEVICE + "\n") // Use string resource
        // Product
        deviceInfo.append(getString(product) + " " + Build.PRODUCT + "\n") // Use string resource
        // Model
        deviceInfo.append(getString(model) + " " + Build.MODEL + "\n") // Use string resource
        // Brand
        deviceInfo.append(getString(brand) + " " + Build.BRAND + "\n") // Use string resource
        // Manufacturer
        deviceInfo.append(getString(manufacturer) + " " + Build.MANUFACTURER + "\n") // Use string resource
        // Android Version
        deviceInfo.append(getString(android_version) + " " + Build.VERSION.RELEASE + "\n") // Use string resource
        // SDK Version
        deviceInfo.append(getString(sdk_version) + " " + Build.VERSION.SDK_INT + "\n") // Use string resource

        // Display the information in the TextView
        deviceInfoTextView.text = deviceInfo.toString()
    }
}
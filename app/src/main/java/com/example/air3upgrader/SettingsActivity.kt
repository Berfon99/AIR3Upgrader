package com.example.air3upgrader

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var modelSpinner: Spinner
    private lateinit var deviceInfoTextView: TextView
    private lateinit var dataStoreManager: DataStoreManager

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        dataStoreManager = DataStoreManager(this)

        modelSpinner = findViewById(R.id.model_spinner)
        deviceInfoTextView = findViewById(R.id.device_info_text_view)

        // Initialize the model list
        modelList = allowedModels.toMutableList()
        val deviceName = getDeviceName()
        modelList.add("use device name: $deviceName")

        // Create an ArrayAdapter using the string array and a default spinner layout
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelList)
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        // Apply the adapter to the spinner
        modelSpinner.adapter = adapter

        // Set the default selection based on the device model
        val deviceModel = Build.MODEL
        val defaultSelection = if (allowedModels.contains(deviceModel)) {
            deviceModel
        } else {
            "use device name: $deviceName"
        }
        modelSpinner.setSelection(modelList.indexOf(defaultSelection))

        // Set a listener to respond to user selections
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedModel = parent.getItemAtPosition(position).toString()
                Log.i("ModelSpinner", "Selected model: $selectedModel")
                // Save the selected model
                lifecycleScope.launch {
                    dataStoreManager.saveSelectedModel(selectedModel)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Another interface callback
            }
        }
        // Get device information
        getDeviceInfo()
    }

    private fun getDeviceName(): String {
        return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME) ?: "Unknown Device"
    }

    private fun getDeviceInfo() {
        val deviceInfo = StringBuilder()
        // Device
        deviceInfo.append("Device: ${Build.DEVICE}\n")
        // Product
        deviceInfo.append("Product: ${Build.PRODUCT}\n")
        // Model
        deviceInfo.append("Model: ${Build.MODEL}\n")
        // Brand
        deviceInfo.append("Brand: ${Build.BRAND}\n")
        // Manufacturer
        deviceInfo.append("Manufacturer: ${Build.MANUFACTURER}\n")
        // Android Version
        deviceInfo.append("Android Version: ${Build.VERSION.RELEASE}\n")
        // SDK Version
        deviceInfo.append("SDK Version: ${Build.VERSION.SDK_INT}\n")

        // Display the information in the TextView
        deviceInfoTextView.text = deviceInfo.toString()
    }
}
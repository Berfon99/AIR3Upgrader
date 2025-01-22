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
import com.example.air3upgrader.R.string.* // Import string resources
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var modelSpinner: Spinner
    private lateinit var deviceInfoTextView: TextView
    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var deviceName: String

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
        val deviceModel = Build.MODEL
        val defaultSelection = if (allowedModels.contains(deviceModel)) {
            deviceModel
        } else {
            deviceName
        }
        modelSpinner.setSelection(modelList.indexOf(defaultSelection))

        // Set a listener to respond to user selections
        modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedDisplayString = parent.getItemAtPosition(position).toString()
                val selectedModel = modelDisplayMap[selectedDisplayString]
                Log.i("ModelSpinner", "Selected model: $selectedModel")
                // Save the selected model
                lifecycleScope.launch {
                    try {
                        dataStoreManager.saveSelectedModel(selectedModel)
                    } catch (e: Exception) {
                        Log.e("SettingsActivity", "Error saving selected model", e)
                    }
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
package com.xc.air3upgrader

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import timber.log.Timber

class ModelSelectionActivity : AppCompatActivity() {

    private lateinit var dataStoreManager: DataStoreManager
    private lateinit var modelList: MutableList<String>
    private lateinit var modelDisplayList: MutableList<String>
    private lateinit var modelDisplayMap: MutableMap<String, String?>
    private lateinit var modelSpinner: Spinner
    private lateinit var deviceName: String
    private var previousSelection: String? = null
    private var isSpinnerInitialized = false

    private val allowedModels = listOf(
        "AIR3-7.2",
        "AIR3-7.3",
        "AIR3-7.3+",
        "AIR3-7.35",
        "AIR3-7.35+"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_model_selection)
        dataStoreManager = DataStoreManager(this)
        modelSpinner = findViewById(R.id.model_spinner)
        val buttonConfirm = findViewById<Button>(R.id.button_confirm)
        deviceName = getDeviceName()
        initModelLists()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelDisplayList)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        modelSpinner.adapter = adapter
        // Determine the default model and set the selection
        val defaultModel = android.os.Build.MODEL
        val defaultModelIndex = if (modelList.contains(defaultModel)) {
            modelList.indexOf(defaultModel)
        } else {
            modelList.indexOf(deviceName)
        }
        modelSpinner.setSelection(defaultModelIndex)
        // Set a listener to respond to user selections
        val spinnerListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                Timber.d("onItemSelected called")
                if (isSpinnerInitialized) {
                    val selectedDisplayString = parent.getItemAtPosition(position).toString()
                    val selectedModel = modelDisplayMap[selectedDisplayString]
                    if (previousSelection != selectedModel) {
                        if (selectedModel == null) {
                            showDeviceNameConfirmationDialog()
                        } else {
                            saveSelectedModel(selectedModel)
                            previousSelection = selectedModel
                        }
                    }
                } else {
                    isSpinnerInitialized = true
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Another interface callback
            }
        }
        modelSpinner.onItemSelectedListener = spinnerListener
        buttonConfirm.setOnClickListener {
            lifecycleScope.launch {
                dataStoreManager.saveManualModelSelected(true)
            }
            val selectedDisplayString = modelSpinner.selectedItem.toString()
            val selectedModel = modelDisplayMap[selectedDisplayString]
            if (selectedModel == null) {
                saveSelectedModel(deviceName)
            } else {
                saveSelectedModel(selectedModel)
            }
            checkNetworkAndContinueLogic()
        }
    }
    private fun initModelLists() {
        // Initialize the model list
        modelList = allowedModels.toMutableList()
        // Check if the device name is already in the allowed models
        if (!modelList.contains(deviceName)) {
            modelList.add(deviceName)
        }
        // Initialize the display list and map
        modelDisplayList = mutableListOf()
        modelDisplayMap = mutableMapOf()
        for (model in allowedModels) {
            modelDisplayList.add(model)
            modelDisplayMap[model] = model
        }
        // Add the device name as the last item with the prefix
        val deviceNameDisplay = getString(R.string.device_name) + " " + deviceName
        modelDisplayList.add(deviceNameDisplay)
        modelDisplayMap[deviceNameDisplay] = null
    }

    private fun checkNetworkAndContinueLogic() {
        lifecycleScope.launch {
            dataStoreManager.saveManualModelSelected(true)
        }
        val intent = Intent(this@ModelSelectionActivity, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
    private fun showDeviceNameConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.device_name_confirmation_title))
        builder.setMessage(getString(R.string.device_name_confirmation_message))
        builder.setPositiveButton(getString(R.string.yes)) { dialog, _ ->
            // Save the device name as the selected model
            saveSelectedModel(deviceName)
            previousSelection = deviceName
            dialog.dismiss()
        }
        builder.setNegativeButton(getString(R.string.no)) { dialog, _ ->
            // Reset the selection to the previous one
            val previousSelectionIndex = modelList.indexOf(previousSelection)
            if (previousSelectionIndex != -1) {
                modelSpinner.setSelection(previousSelectionIndex)
            }
            dialog.dismiss()
        }
        builder.show()
    }

    private fun saveSelectedModel(selectedModel: String) {
        lifecycleScope.launch {
            dataStoreManager.saveSelectedModel(selectedModel)
        }
    }

    private fun getDeviceName(): String {
        return Settings.Global.getString(contentResolver, Settings.Global.DEVICE_NAME)
            ?: getString(R.string.unknown_device) // Use string resource
    }
    internal fun getAllowedModels(): List<String> {
        return allowedModels
    }
    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }
}
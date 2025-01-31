package com.xc.air3upgrader

import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.xc.air3upgrader.R.string.*

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // Find the TextViews
        val versionTextView = findViewById<TextView>(R.id.versionTextView)
        val copyrightTextView = findViewById<TextView>(R.id.copyrightTextView)
        val deviceInfoTextView = findViewById<TextView>(R.id.deviceInfoTextView)

        // Set the text
        versionTextView.text = getString(R.string.app_version) + " " + BuildConfig.VERSION_NAME
        copyrightTextView.text = getString(R.string.copyright)

        // Get device information
        getDeviceInfo(deviceInfoTextView)
    }

    private fun getDeviceInfo(deviceInfoTextView: TextView) {
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
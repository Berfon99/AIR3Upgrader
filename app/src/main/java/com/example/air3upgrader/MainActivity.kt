package com.example.air3upgrader

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.air3upgrader.R

class MainActivity : AppCompatActivity() {

    private lateinit var xctrackName: TextView
    private lateinit var xcguideName: TextView
    private lateinit var air3managerName: TextView
    private lateinit var closeButton: Button
    private lateinit var deviceInfoTextView: TextView

    // Package names of the apps we want to check
    private val xctrackPackageName = "org.xcontest.XCTrack"
    private val xcguidePackageName = "indysoft.xc_guide"
    private val air3managerPackageName = "com.xc.r3"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize TextViews
        xctrackName = findViewById(R.id.xctrack_name)
        xcguideName = findViewById(R.id.xcguide_name)
        air3managerName = findViewById(R.id.air3manager_name)
        closeButton = findViewById(R.id.close_button)
        deviceInfoTextView = findViewById(R.id.device_info_text_view)

        // Check if the apps are installed and update the UI
        checkAppInstallation(xctrackPackageName, xctrackName)
        checkAppInstallation(xcguidePackageName, xcguideName)
        checkAppInstallation(air3managerPackageName, air3managerName)

        // Set onClick listener for the close button
        closeButton.setOnClickListener {
            finish() // Close the app
        }

        // Get device information
        getDeviceInfo()
    }

    private fun checkAppInstallation(packageName: String, nameTextView: TextView) {
        val packageManager: PackageManager = this.packageManager
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
            if (packageInfo != null) {
                Log.i("AppCheck", "$packageName: getPackageInfo() returned a result")
                // App is installed
                nameTextView.text = getAppName(packageName)
                nameTextView.background = ContextCompat.getDrawable(this, R.drawable.circle_background_green)

                // Get the version name
                val versionName = packageInfo.versionName
                // Get the version code
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    packageInfo.versionCode.toLong()
                }

                // Find the version TextView
                val versionTextViewId = when (packageName) {
                    xctrackPackageName -> R.id.xctrack_version
                    xcguidePackageName -> R.id.xcguide_version
                    air3managerPackageName -> R.id.air3manager_version
                    else -> null
                }

                if (versionTextViewId != null) {
                    val versionTextView = findViewById<TextView>(versionTextViewId)
                    if (packageName == air3managerPackageName) {
                        versionTextView.text = "v$versionName ($versionCode)"
                    } else {
                        versionTextView.text = "v$versionName"
                    }
                }
            } else {
                Log.i("AppCheck", "$packageName: getPackageInfo() returned null")
                // App is not installed
                nameTextView.text = getAppName(packageName)
                nameTextView.background = ContextCompat.getDrawable(this, R.drawable.circle_background)
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e("AppCheck", "$packageName: NameNotFoundException", e)
            // App is not installed
            nameTextView.text = getAppName(packageName)
            nameTextView.background = ContextCompat.getDrawable(this, R.drawable.circle_background)
        }
    }

    private fun getAppName(packageName: String): String {
        val packageManager = packageManager
        return try {
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // Handle the case where the app is not found
            when (packageName) {
                xctrackPackageName -> "XCTrack"
                xcguidePackageName -> "XC Guide"
                air3managerPackageName -> "AIR³ Manager"
                else -> "Unknown App"
            }
        }
    }

    private fun getDeviceInfo() {
        val deviceInfo = StringBuilder()

        deviceInfo.append("Device Information:\n")
        deviceInfo.append("--------------------\n")

        // Device
        deviceInfo.append("Device: ${Build.DEVICE}\n")
        deviceInfo.append("Product: ${Build.PRODUCT}\n")

        // Display the information in Logcat
        Log.i("DeviceInfo", deviceInfo.toString())

        // Display the information in the TextView
        deviceInfoTextView.text = deviceInfo.toString()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                // Open the settings window
                val intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
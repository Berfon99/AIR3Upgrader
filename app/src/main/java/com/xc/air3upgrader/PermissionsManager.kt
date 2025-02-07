package com.xc.air3upgrader

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import timber.log.Timber

@SuppressLint("unused")
class PermissionsManager(private val activity: ComponentActivity) {

    companion object {
        fun getClassName(): String {
            return PermissionsManager::class.java.name
        }
        const val REQUEST_CODE_INSTALL_PACKAGES = 1001
        const val REQUEST_CODE_OVERLAY_PERMISSION = 1002
    }
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    @SuppressLint("unused")
    private var installPermissionGranted = false
    private var overlayPermissionGranted = false
    private lateinit var lifecycleScope: LifecycleCoroutineScope
    private lateinit var onPermissionGranted: () -> Unit
    private lateinit var onPermissionDenied: () -> Unit

    fun checkInstallPermission(): Boolean {
        Timber.d("checkInstallPermission: called")
        val packageManager = activity.packageManager
        val canRequestPackageInstalls = packageManager.canRequestPackageInstalls()
        Timber.d("checkInstallPermission: end")
        return canRequestPackageInstalls
    }
    fun requestInstallPermission() {
        Timber.d("requestInstallPermission: called")
        if (!checkInstallPermission()) {
            Timber.d("requestInstallPermission: Install permission not granted, requesting permission")
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivityForResult(intent, REQUEST_CODE_INSTALL_PACKAGES)
        }
        Timber.d("requestInstallPermission: end")
    }
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Timber.d("onRequestPermissionsResult: called")
        when (requestCode) {
            0 -> { // Notification permission request code
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Timber.d("onRequestPermissionsResult: Notification permission granted")
                    requestInstallPermission() // Request the next permission
                } else {
                    Timber.d("onRequestPermissionsResult: Notification permission denied")
                    Toast.makeText(activity, "Notification permission is required.", Toast.LENGTH_LONG).show()
                }
            }
        }
        Timber.d("onRequestPermissionsResult: end")
    }
    fun checkAllPermissionsGranted(): Boolean {
        Timber.d("checkAllPermissionsGranted: called")
        val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No notification permission needed before Android 13
        }
        val installPermissionGranted = checkInstallPermission()
        Timber.d("checkAllPermissionsGranted: notificationPermissionGranted: $notificationPermissionGranted")
        Timber.d("checkAllPermissionsGranted: installPermissionGranted: $installPermissionGranted")
        Timber.d("checkAllPermissionsGranted: end")
        return notificationPermissionGranted && installPermissionGranted
    }
    fun showPermissionExplanationDialog(onPermissionRequested: () -> Unit) {
        Timber.d("showPermissionExplanationDialog: called")
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.permissions_required_title))
            .setMessage(activity.getString(R.string.permissions_required_message))
            .setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
                Timber.d("showPermissionExplanationDialog: OK button clicked")
                onPermissionRequested() // This line is the key change!
                Timber.d("showPermissionExplanationDialog: requestAllPermissions() called")
            }
            .setNegativeButton(activity.getString(R.string.cancel)) { _, _ ->
                activity.finish()
            }
            .setCancelable(false) // Prevent dismissing by tapping outside
            .show()
        Timber.d("showPermissionExplanationDialog: end")
    }
    fun requestNotificationPermission(requestPermissionLauncher: ActivityResultLauncher<String>) {
        Timber.d("requestNotificationPermission: called")
        this.requestPermissionLauncher = requestPermissionLauncher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Check if the permission is already granted
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                // Permission is already granted
                Timber.d("Notification permission already granted")
            } else {
                // Permission is not granted, request it
                Timber.d("Notification permission not granted, requesting it")
                // Launch the permission request
                this.requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // On older versions, the permission is granted at install time
            Timber.d("Notification permission granted at install time")
        }
        Timber.d("requestNotificationPermission: end")
    }
    fun checkAllPermissionsGrantedAndContinue(onContinueSetup: () -> Unit) {
        Timber.d("checkAllPermissionsGrantedAndContinue: called")
        if (checkAllPermissionsGranted()) {
            Timber.d("checkAllPermissionsGrantedAndContinue: All permissions granted, calling continueSetup()")
            onContinueSetup()
        } else {
            Timber.d("checkAllPermissionsGrantedAndContinue: Not all permissions granted")
        }
        Timber.d("checkAllPermissionsGrantedAndContinue: end")
    }
    fun requestAllPermissions(requestPermissionLauncher: ActivityResultLauncher<String>) {
        Timber.d("requestAllPermissions: called")
        requestNotificationPermission(requestPermissionLauncher)
        Timber.d("requestAllPermissions: end")
    }
    fun setupOverlayPermissionLauncher(
        overlayPermissionLauncher: ActivityResultLauncher<Intent>,
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit,
        lifecycleScope: LifecycleCoroutineScope
    ) {
        Timber.d("setupOverlayPermissionLauncher: called")
        this.overlayPermissionLauncher = overlayPermissionLauncher
        this.lifecycleScope = lifecycleScope
        this.onPermissionGranted = onPermissionGranted
        this.onPermissionDenied = onPermissionDenied
        Timber.d("setupOverlayPermissionLauncher: end")
    }
    fun checkOverlayPermission(context: Context, packageName: String) {
        Timber.d("checkOverlayPermission: called")
        if (!Settings.canDrawOverlays(context)) {
            Timber.d("checkOverlayPermission: SYSTEM_ALERT_WINDOW permission not granted, requesting permission")
            requestOverlayPermission(packageName)
        } else {
            Timber.d("checkOverlayPermission: SYSTEM_ALERT_WINDOW permission already granted")
            overlayPermissionGranted = true
        }
        Timber.d("checkOverlayPermission: end")
    }
    private fun requestOverlayPermission(packageName: String) {
        Timber.d("requestOverlayPermission: called")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
        Timber.d("requestOverlayPermission: end")
    }
    fun handleOverlayPermissionResult(
        requestCode: Int,
        resultCode: Int,
        dataStoreManager: DataStoreManager,
        enableBackgroundCheckCheckbox: android.widget.CheckBox,
        updateFlagsValues: () -> Unit,
        updateUiState: (Boolean) -> Unit
    ) {
        Timber.d("handleOverlayPermissionResult: called")
        if (requestCode == REQUEST_CODE_OVERLAY_PERMISSION) {
            if (Settings.canDrawOverlays(activity)) {
                Timber.d("handleOverlayPermissionResult: Display over other apps permission granted")
                onPermissionGranted()
            } else {
                Timber.d("handleOverlayPermissionResult: Display over other apps permission not granted")
                enableBackgroundCheckCheckbox.isChecked = false
                onPermissionDenied()
            }
        }
        Timber.d("handleOverlayPermissionResult: end")
    }
}
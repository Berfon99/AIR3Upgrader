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
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var lifecycleScope: LifecycleCoroutineScope
    private lateinit var onPermissionGranted: () -> Unit
    private lateinit var onPermissionDenied: () -> Unit
    private var onInstallPermissionResult: (() -> Unit)? = null

    private val installPermissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.d("installPermissionLauncher: called")
            if (checkInstallPermission()) {
                Timber.d("installPermissionLauncher: Install permission granted")
            } else {
                Timber.d("installPermissionLauncher: Install permission denied")
                Toast.makeText(activity, "Install permission is required.", Toast.LENGTH_LONG).show()
            }
            onInstallPermissionResult?.invoke()
            Timber.d("installPermissionLauncher: end")
        }

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
            installPermissionLauncher.launch(intent)
        } else {
            onInstallPermissionResult?.invoke()
        }
        Timber.d("requestInstallPermission: end")
    }

    fun showPermissionExplanationDialog(onPermissionRequested: () -> Unit, onInstallPermissionResult: () -> Unit) {
        Timber.d("showPermissionExplanationDialog: called")
        this.onInstallPermissionResult = onInstallPermissionResult
        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.permissions_required_title))
            .setMessage(activity.getString(R.string.permissions_required_message))
            .setPositiveButton(activity.getString(R.string.ok)) { _, _ ->
                Timber.d("showPermissionExplanationDialog: OK button clicked")
                onPermissionRequested()
                Timber.d("showPermissionExplanationDialog: requestAllPermissions() called")
            }
            .setNegativeButton(activity.getString(R.string.cancel)) { _, _ ->
                activity.finish()
            }
            .setCancelable(false)
            .show()
        Timber.d("showPermissionExplanationDialog: end")
    }

    fun requestNotificationPermission(requestPermissionLauncher: ActivityResultLauncher<String>, onInstallPermissionResult: () -> Unit) {
        Timber.d("requestNotificationPermission: called")
        this.requestPermissionLauncher = requestPermissionLauncher
        this.onInstallPermissionResult = onInstallPermissionResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.d("Notification permission not granted, requesting it")
                this.requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Timber.d("Notification permission already granted")
                onInstallPermissionResult()
            }
        } else {
            Timber.d("Notification permission granted at install time")
            onInstallPermissionResult()
        }
        Timber.d("requestNotificationPermission: end")
    }

    fun checkAllPermissionsGranted(): Boolean {
        Timber.d("checkAllPermissionsGranted: called")
        val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        val installPermissionGranted = checkInstallPermission()
        Timber.d("checkAllPermissionsGranted: notificationPermissionGranted: $notificationPermissionGranted")
        Timber.d("checkAllPermissionsGranted: installPermissionGranted: $installPermissionGranted")
        Timber.d("checkAllPermissionsGranted: end")
        return notificationPermissionGranted && installPermissionGranted
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

    fun requestAllPermissions(requestPermissionLauncher: ActivityResultLauncher<String>, onInstallPermissionResult: () -> Unit) {
        Timber.d("requestAllPermissions: called")
        this.onInstallPermissionResult = onInstallPermissionResult
        this.requestPermissionLauncher = requestPermissionLauncher
        requestNotificationPermission(requestPermissionLauncher, onInstallPermissionResult)
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
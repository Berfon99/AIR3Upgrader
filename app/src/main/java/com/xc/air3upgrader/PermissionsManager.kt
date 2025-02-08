package com.xc.air3upgrader

import android.app.Activity
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import android.widget.CheckBox
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import timber.log.Timber
import kotlinx.coroutines.launch
import kotlinx.coroutines.*

@SuppressLint("unused")
class PermissionsManager(private val context: Context, private val dataStoreManager: DataStoreManager) {

    companion object {
        fun getClassName(): String {
            return PermissionsManager::class.java.name
        }
        const val REQUEST_CODE_INSTALL_PACKAGES = 1001
        const val REQUEST_CODE_OVERLAY_PERMISSION = 1002
    }

    private lateinit var overlayPermissionLauncher: ActivityResultLauncher<Intent>
    private lateinit var lifecycleScope: LifecycleCoroutineScope
    private lateinit var onPermissionGranted: () -> Unit
    private lateinit var onPermissionDenied: () -> Unit
    private var onInstallPermissionResult: (() -> Unit)? = null

    private val installPermissionLauncher =
        (context as ComponentActivity).registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Timber.d("installPermissionLauncher: called")
            if (checkInstallPermission()) {
                Timber.d("installPermissionLauncher: Install permission granted")
                onInstallPermissionResult?.invoke()
            } else {
                Timber.d("installPermissionLauncher: Install permission denied")
                Toast.makeText(context, "Install permission is required.", Toast.LENGTH_LONG).show()
                showInstallPermissionDeniedMessage()
            }
            Timber.d("installPermissionLauncher: end")
        }
    fun checkInstallPermission(): Boolean {
        Timber.d("checkInstallPermission: called")
        val result = context.packageManager.canRequestPackageInstalls()
        Timber.d("checkInstallPermission: result: $result")
        Timber.d("checkInstallPermission: end")
        return result
    }
    private fun isInstallPermissionGranted(): Boolean {
        Timber.d("isInstallPermissionGranted: called")
        val isGranted = checkInstallPermission()
        Timber.d("isInstallPermissionGranted: end")
        return isGranted
    }
    fun requestInstallPermission(onInstallPermissionResult: () -> Unit) {
        Timber.d("requestInstallPermission: called")
        this.onInstallPermissionResult = onInstallPermissionResult
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Timber.d("requestInstallPermission: requesting install permission")
                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                intent.data = Uri.parse("package:${context.packageName}")
                installPermissionLauncher.launch(intent)
            } else {
                Timber.d("requestInstallPermission: Install permission already granted")
                onInstallPermissionResult()
            }
        } else {
            Timber.d("requestInstallPermission: Install permission granted at install time")
            onInstallPermissionResult()
        }
        Timber.d("requestInstallPermission: end")
    }
    private fun showInstallPermissionDeniedMessage() {
        Timber.d("showInstallPermissionDeniedMessage: called")
        AlertDialog.Builder(context)
            .setTitle("Install Permission Required")
            .setMessage("Install permission is required to install apps. Please grant the permission.")
            .setPositiveButton("OK") { _, _ ->
                (context as? Activity)?.finish()
            }
            .show()
        Timber.d("showInstallPermissionDeniedMessage: end")
    }
    fun showPermissionExplanationDialog(onPermissionRequested: () -> Unit, onInstallPermissionResult: () -> Unit) {
        Timber.d("showPermissionExplanationDialog: called")
        this.onInstallPermissionResult = onInstallPermissionResult
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Permissions Required")
        builder.setMessage("This app needs notification and install permissions to function properly.")
        builder.setPositiveButton("OK") { dialog, _ ->
            Timber.d("showPermissionExplanationDialog: OK button clicked")
            onPermissionRequested()
            dialog.dismiss()
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            Timber.d("showPermissionExplanationDialog: Cancel button clicked")
            dialog.dismiss()
        }
        val dialog = builder.create()
        dialog.show()
        Timber.d("showPermissionExplanationDialog: end")
    }
    fun requestNotificationPermission(requestPermissionLauncher: ActivityResultLauncher<String>) {
        Timber.d("requestNotificationPermission: called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Timber.d("Notification permission not granted, requesting it")
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                Timber.d("Notification permission already granted")
            }
        } else {
            Timber.d("Notification permission granted at install time")            
        }
    }
    internal fun showNotificationPermissionDeniedMessage() {
        AlertDialog.Builder(context)
            .setTitle("Notification Permission Required")
            .setMessage("Notification permission is required to receive notifications. Please grant the permission.")
            .setPositiveButton("OK") { _, _ -> (context as? Activity)?.finish() }
            .show()
        }
    fun checkAllPermissionsGrantedAndContinue(requestPermissionLauncher: ActivityResultLauncher<String>) {
        Timber.d("checkAllPermissionsGrantedAndContinue: called")
        if (checkAllPermissionsGranted()) {
            Timber.d("checkAllPermissionsGrantedAndContinue: All permissions granted, calling continueSetup()")
        } else {
            Timber.d("checkAllPermissionsGrantedAndContinue: Permissions not granted, showing explanation dialog")
            showPermissionExplanationDialog(
                onPermissionRequested = { requestInstallPermission { requestNotificationPermission(requestPermissionLauncher) } },
                onInstallPermissionResult = { }
            ) // Added the missing closing parenthesis here
        }
        Timber.d("checkAllPermissionsGrantedAndContinue: end")
    }
    fun checkAllPermissionsGranted(): Boolean {
        Timber.d("checkAllPermissionsGranted: called")
        val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
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
    fun checkOverlayPermission(context: Context, packageName: String, enableBackgroundCheckCheckbox: CheckBox) {
        Timber.d("checkOverlayPermission: called")

        if (!Settings.canDrawOverlays(context)) {
            enableBackgroundCheckCheckbox.isChecked = false
            onPermissionDenied()

            Timber.d("checkOverlayPermission: SYSTEM_ALERT_WINDOW permission not granted, requesting permission")
            requestOverlayPermission(packageName)

            // Attendre un court instant et revérifier après le retour de la permission
            lifecycleScope.launch {
                delay(500) // Attendre 500ms pour s'assurer que le système a bien mis à jour l'autorisation
                if (Settings.canDrawOverlays(context)) {
                    withContext(Dispatchers.Main) {
                        enableBackgroundCheckCheckbox.isChecked = true
                    }
                    onPermissionGranted()
                }
            }

        } else {
            Timber.d("checkOverlayPermission: SYSTEM_ALERT_WINDOW permission already granted")
            enableBackgroundCheckCheckbox.isChecked = true
            onPermissionGranted()
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

        lifecycleScope.launch {
            delay(500) // Petite attente pour s'assurer que la permission est prise en compte
            if (Settings.canDrawOverlays(context)) {
                Timber.d("requestOverlayPermission: Permission granted, updating UI")
                onPermissionGranted() // Mettre à jour immédiatement l'UI
            }
        }

        Timber.d("requestOverlayPermission: end")
    }
}
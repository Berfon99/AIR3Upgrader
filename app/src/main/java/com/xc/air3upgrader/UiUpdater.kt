package com.xc.air3upgrader

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import timber.log.Timber
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import android.os.Build
import android.util.Log

object UiUpdater {
    private fun updateApkNameDisplay(appInfo: AppInfo, apkNameTextView: TextView?) {
        Timber.d("updateApkNameDisplay() called for app: ${appInfo.name}")
        val apkName = appInfo.apkPath.substringAfterLast('/')
        Timber.d("APK name: $apkName")
        apkNameTextView?.text = apkName
        apkNameTextView?.visibility = View.VISIBLE
    }

    fun updateAppInfo(
        context: Context,
        appInfo: AppInfo,
        nameTextView: TextView,
        serverVersionTextView: TextView,
        installedVersionTextView: TextView?
    ) {
        Timber.d("updateAppInfo() called for app: ${appInfo.name}")
        nameTextView.text = appInfo.name
        serverVersionTextView.text = context.getString(R.string.server_version, appInfo.highestServerVersion)
        val installedVersion = getInstalledVersion(context, appInfo.`package`)
        Timber.d("Installed version for ${appInfo.`package`}: $installedVersion")
        installedVersionTextView?.text =
            if (installedVersion != null) context.getString(R.string.installed) + " " + installedVersion else context.getString(R.string.not_installed)
        setAppBackgroundColor(context, appInfo, nameTextView, installedVersionTextView)
        val apkNameTextView = when (appInfo.`package`) {
            "org.xcontest.XCTrack" -> (context as Activity).findViewById<TextView>(R.id.xctrack_apk_name)
            "indysoft.xc_guide" -> (context as Activity).findViewById<TextView>(R.id.xcguide_apk_name)
            "com.xc.r3" -> (context as Activity).findViewById<TextView>(R.id.air3manager_apk_name)
            else -> null
        }
        if (appInfo.isSelectedForUpgrade) {
            updateApkNameDisplay(appInfo, apkNameTextView)
        } else {
            apkNameTextView?.visibility = View.GONE
        }
    }

    private fun getInstalledVersion(context: Context, packageName: String): String? {
        Timber.d("getInstalledVersion() called for package: $packageName")
        return try {
            val packageInfo = context.packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            Timber.d("Raw version name for $packageName: $versionName")
            val filteredVersion = AppUtils.getAppVersion(context, packageName)
            Timber.d("Filtered version for $packageName: $filteredVersion")
            filteredVersion
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e, "Package not found: $packageName")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error getting version for $packageName")
            null
        }
    }
    internal fun setAppBackgroundColor(
        context: Context,
        appInfo: AppInfo,
        nameTextView: TextView,
        installedVersionTextView: TextView?
    ) {
        val installedVersion = getInstalledVersion(context, appInfo.`package`)
        Timber.d("setAppBackgroundColor() called for ${appInfo.name}")
        Timber.d("  Installed Version: $installedVersion")
        Timber.d("  Highest Server Version: ${appInfo.highestServerVersion}")
        Timber.d("  installedVersionTextView?.text: ${installedVersionTextView?.text}")

        val color = if (installedVersionTextView?.text == context.getString(R.string.not_installed)) {
            Timber.d("  Color: Not Installed")
            ContextCompat.getColor(context, R.color.not_installed_color)
        } else if (!VersionComparator.isServerVersionHigher(installedVersion ?: "", appInfo.highestServerVersion, appInfo.`package`)) {
            Timber.d("  Color: Up-to-Date")
            ContextCompat.getColor(context, R.color.up_to_date_color)
        } else {
            Timber.d("  Color: Update Available")
            ContextCompat.getColor(context, R.color.update_available_color)
        }
        nameTextView.setBackgroundColor(color)
    }
    fun updateUIAfterNoInternet(
        xctrackVersion: TextView,
        xcguideVersion: TextView,
        air3managerVersion: TextView,
        xctrackServerVersion: TextView,
        xcguideServerVersion: TextView,
        air3managerServerVersion: TextView,
        context: Context
    ) {
        xctrackVersion.text = context.getString(R.string.version_not_found)
        xcguideVersion.text = context.getString(R.string.version_not_found)
        air3managerVersion.text = context.getString(R.string.version_not_found)
        xctrackServerVersion.text = context.getString(R.string.version_not_found)
        xcguideServerVersion.text = context.getString(R.string.version_not_found)
        air3managerServerVersion.text = context.getString(R.string.version_not_found)
    }
    fun checkAppInstallationForApp(
        context: Context,
        packageName: String,
        appNameTextView: TextView,
        appVersionTextView: TextView,
        appInfos: List<AppInfo>,
        xctrackPackageName: String,
        xctrackServerVersion: TextView,
        xctrackCheckbox: CheckBox,
        xcguidePackageName: String,
        xcguideServerVersion: TextView,
        xcguideCheckbox: CheckBox,
        air3managerPackageName: String,
        air3managerServerVersion: TextView,
        air3managerCheckbox: CheckBox,
        coroutineScope: CoroutineScope
    ) {
        Timber.d("checkAppInstallationForApp() called for package: $packageName")
        val installedVersion = AppUtils.getAppVersion(context, packageName)
        Timber.d("  Installed version for $packageName: $installedVersion")
        appVersionTextView.text = if (installedVersion != context.getString(R.string.na)) context.getString(R.string.installed) + " " + installedVersion else context.getString(R.string.not_installed)

        coroutineScope.launch {
            val appInfo = appInfos.find { it.`package` == packageName }
            Timber.d("  appInfo for $packageName: $appInfo")
            val serverVersion = appInfo?.highestServerVersion
            Timber.d("  Server version for $packageName: $serverVersion")
            if (serverVersion != null) {
                val serverVersionToDisplay = if (packageName == xctrackPackageName) {
                    serverVersion.replace("-", ".")
                } else {
                    serverVersion
                }
                when (packageName) {
                    xctrackPackageName -> xctrackServerVersion.text = context.getString(R.string.server) + " " + serverVersionToDisplay
                    xcguidePackageName -> xcguideServerVersion.text = context.getString(R.string.server) + " " + serverVersionToDisplay
                    air3managerPackageName -> air3managerServerVersion.text = context.getString(R.string.server) + " " + serverVersionToDisplay
                }

                Timber.d("  Calling VersionComparator.isServerVersionHigher() with: installedVersion=$installedVersion, serverVersion=$serverVersion, packageName=$packageName")
                if (VersionComparator.isServerVersionHigher(installedVersion, serverVersion, packageName)) {
                    Timber.d("  Server version is higher for $packageName")
                    // Une nouvelle version est disponible, cocher la case "Upgrade"
                    when (packageName) {
                        xctrackPackageName -> xctrackCheckbox.isChecked = true
                        xcguidePackageName -> xcguideCheckbox.isChecked = true
                        air3managerPackageName -> air3managerCheckbox.isChecked = true
                    }
                } else {
                    Timber.d("  Server version is not higher for $packageName")
                    // La version du serveur est la même que celle installée, laisser la case décochée
                    when (packageName) {
                        xctrackPackageName -> xctrackCheckbox.isChecked = false
                        xcguidePackageName -> xcguideCheckbox.isChecked = false
                        air3managerPackageName -> air3managerCheckbox.isChecked = false
                    }
                    //  Activer la case pour permettre à l'utilisateur de la sélectionner manuellement
                    when (packageName) {
                        xctrackPackageName -> xctrackCheckbox.isEnabled = true
                        xcguidePackageName -> xcguideCheckbox.isEnabled = true
                        air3managerPackageName -> air3managerCheckbox.isEnabled = true
                    }
                }
            } else {
                Timber.d("  Server version is null for $packageName")
                // Gérer le cas où la version du serveur n'est pas disponible
                when (packageName) {
                    xctrackPackageName -> xctrackServerVersion.text = context.getString(R.string.version_not_found)
                    xcguidePackageName -> xcguideServerVersion.text = context.getString(R.string.version_not_found)
                    air3managerPackageName -> air3managerServerVersion.text = context.getString(R.string.version_not_found)
                }
            }
            coroutineScope.launch {
                Timber.d("Before calling setAppBackgroundColor")
                val appInfo = appInfos.find { it.`package` == packageName }
                if (appInfo != null) {
                    setAppBackgroundColor(context, appInfo, appNameTextView, appVersionTextView)
                } else {
                    Timber.e("AppInfo is null for package: $packageName")
                }
                Timber.d("After calling setAppBackgroundColor")
            }
        }
    }
    fun setActionBarTitleWithSelectedModel(
        context: Context,
        dataStoreManager: DataStoreManager,
        getSettingsAllowedModels: () -> List<String>,
        getDeviceName: () -> String,
        coroutineScope: CoroutineScope,
        supportActionBar: androidx.appcompat.app.ActionBar?
    ) {
        coroutineScope.launch {
            // Delay the initial read to allow SettingsActivity to initialize
            val selectedModel = dataStoreManager.getSelectedModel().firstOrNull()
            val deviceModel = Build.MODEL
            val finalSelectedModel = when {
                selectedModel == null -> deviceModel
                selectedModel.isEmpty() -> deviceModel
                dataStoreManager.isDeviceModelSupported(selectedModel, getSettingsAllowedModels()) -> selectedModel
                else -> {
                    Log.e("MainActivity", "Unsupported model selected: $selectedModel")
                    getDeviceName()
                }
            }
            dataStoreManager.getSelectedModel().collectLatest { selectedModel ->
                val androidVersion = Build.VERSION.RELEASE // Get the Android version
                supportActionBar?.title = "AIR³ Upgrader - $finalSelectedModel - Android $androidVersion" // Set the title correctly
            }
        }
    }
}
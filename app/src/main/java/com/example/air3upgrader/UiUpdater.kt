package com.example.air3upgrader

import android.content.Context
import android.widget.CheckBox
import android.widget.TextView
import com.example.air3upgrader.R.string.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UiUpdater {

    fun updateAppInfo(
        context: Context,
        appInfo: AppInfo,
        nameTextView: TextView,
        serverVersionTextView: TextView,
        installedVersionTextView: TextView?,
        selectedModel: String?
    ) {
        nameTextView.text = appInfo.name
        serverVersionTextView.text = context.getString(server) + " " + appInfo.latestVersion
        installedVersionTextView?.text = if (appInfo.installedVersion != context.getString(na)) context.getString(installed) + " " + appInfo.installedVersion else context.getString(not_installed)
        CoroutineScope(Dispatchers.Main).launch {
            try {
                AppUtils.setAppBackgroundColor(context, appInfo.`package`, nameTextView, appInfo.installedVersion, selectedModel)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateCheckboxState(context: Context, appInfo: AppInfo, checkBox: CheckBox) {
        checkBox.isChecked = VersionComparator.isServerVersionHigher(appInfo.installedVersion ?: "", appInfo.latestVersion, appInfo.`package`)
        checkBox.isEnabled = appInfo.installedVersion != appInfo.latestVersion
    }
}
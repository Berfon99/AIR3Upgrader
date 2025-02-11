package com.xc.air3upgrader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.appcompat.app.AlertDialog

object NetworkUtils {

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }

    fun isWifiConnected(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    fun showNoInternetDialog(context: Context, retryAction: () -> Unit, updateUiAction: () -> Unit) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.no_internet_connection))
            .setMessage(context.getString(R.string.no_internet_message))
            .setPositiveButton(context.getString(R.string.ok)) { dialog, _ ->
                dialog.dismiss()
                // Update UI to show "version not found"
                updateUiAction()
            }
            .setNegativeButton(context.getString(R.string.retry)) { dialog, _ ->
                // Retry the process
                if (!isNetworkAvailable(context)) {
                    retryAction()
                }
            }
            .setCancelable(false) // Prevent dismissing by tapping outside
            .create() // Create the dialog
        dialog.show() // Show the dialog
    }
}
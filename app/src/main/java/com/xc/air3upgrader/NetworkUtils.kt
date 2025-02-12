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
                updateUiAction()
            }
            .setNegativeButton(context.getString(R.string.retry)) { dialog, _ ->
                // Retry the process
                if (!isNetworkAvailable(context)) {
                    retryAction()
                } else {
                    // If network is available, disable the retry button
                    val retryButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE)
                    retryButton.isEnabled = false
                }
            }
            .setCancelable(false) // Prevent dismissing by tapping outside
            .create() // Create the dialog
        dialog.show() // Show the dialog
        // Check network availability and disable retry button if needed
        if (isNetworkAvailable(context)) {
            val retryButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
            retryButton.isEnabled = false
        }
    }
    fun checkNetworkAndContinue(context: Context, continueAction: () -> Unit, updateUiAction: () -> Unit) {
        if (isNetworkAvailable(context)) {
            continueAction()
        } else {
            showNoInternetDialog(
                context,
                retryAction = {
                    checkNetworkAndContinue(context, continueAction, updateUiAction)
                },
                updateUiAction = {
                    updateUiAction()
                }
            )
        }
    }
}
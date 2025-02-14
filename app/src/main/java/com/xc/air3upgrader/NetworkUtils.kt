package com.xc.air3upgrader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.appcompat.app.AlertDialog

object NetworkUtils {

    interface NetworkDialogListener {
        fun onNoInternetAgreed()
    }
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
    fun showNoInternetDialog(context: Context, message: String, retryAction: () -> Unit, listener: NetworkDialogListener) {
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.no_internet_title))
            .setMessage(message)
            .setPositiveButton(context.getString(R.string.ok)) { dialog, _ ->
                listener.onNoInternetAgreed()
                dialog.dismiss()
            }
            .setNegativeButton(context.getString(R.string.retry)) { dialog, _ ->
                dialog.dismiss()
                retryAction()
            }
            .setCancelable(false) // Prevent dismissing by tapping outside
            .create() // Create the dialog
        dialog.show() // Show the dialog
    }
    fun checkNetworkAndContinue(context: Context, isWifiOnly: Boolean, retryAction: () -> Unit, listener: NetworkDialogListener): Boolean {
        val isWifiConnected = isWifiConnected(context)
        val isNetworkAvailable = isNetworkAvailable(context)
        val isNetworkOk = if (isWifiOnly) {
            isWifiConnected
        } else {
            isNetworkAvailable
        }
        return if (!isNetworkOk) {
            val message = if (isWifiOnly && !isWifiConnected) {
                context.getString(R.string.wifi_only_no_wifi)
            } else {
                context.getString(R.string.no_internet_message)
            }
            showNoInternetDialog(
                context,
                message,
                retryAction = {
                    retryAction()
                },
                listener = listener
            )
            false
        } else {
            true
        }
    }
}
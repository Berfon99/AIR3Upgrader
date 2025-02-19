package com.xc.air3upgrader

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber

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

    fun showNoInternetDialog(
        context: Context,
        message: String,
        retryAction: () -> Unit,
        listener: NetworkDialogListener
    ) {
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

    fun checkNetworkAndContinue(
        context: Context,
        isWifiOnly: Boolean,
        retryAction: () -> Unit,
        listener: NetworkDialogListener
    ): Boolean {
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

    fun shouldShowDataUsageWarning(dataStoreManager: DataStoreManager): Flow<Boolean> {
        return combine(
            dataStoreManager.getWifiOnly(),
            dataStoreManager.getDataUsageWarningAccepted()
        ) { isWifiOnly, isDataUsageWarningAccepted ->
            Timber.d("shouldShowDataUsageWarning: isWifiOnly=$isWifiOnly, isDataUsageWarningAccepted=$isDataUsageWarningAccepted")
            !isWifiOnly && !isDataUsageWarningAccepted
        }
    }

    fun showDataUsageWarningDialog(
        context: Context,
        dataStoreManager: DataStoreManager,
        onAccept: () -> Unit
    ) {
        Timber.d("showDataUsageWarningDialog: Dialog is about to be created")
        val dialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.data_usage_warning_title))
            .setMessage(context.getString(R.string.data_usage_warning_message))
            .setPositiveButton(context.getString(R.string.wifi_only)) { dialog, _ ->
                Timber.d("showDataUsageWarningDialog: Wifi Only button clicked")
                // Launch a coroutine to call the suspend function
                (context as? androidx.appcompat.app.AppCompatActivity)?.lifecycleScope?.launch {
                    Timber.d("showDataUsageWarningDialog: Coroutine launched for saveWifiOnly")
                    dataStoreManager.saveWifiOnly(true)
                    Timber.d("showDataUsageWarningDialog: saveWifiOnly completed")
                }
                Timber.d("showDataUsageWarningDialog: Dialog dismissed after Wifi Only click")
                dialog.dismiss()
            }
            .setNegativeButton(context.getString(R.string.accept_and_continue)) { dialog, _ ->
                Timber.d("showDataUsageWarningDialog: Accept and Continue button clicked")
                // Call onAccept() before dismissing the dialog
                Timber.d("showDataUsageWarningDialog: Calling onAccept()")
                onAccept()
                Timber.d("showDataUsageWarningDialog: onAccept() returned")
                // Launch a coroutine to call the suspend function
                (context as? androidx.appcompat.app.AppCompatActivity)?.lifecycleScope?.launch {
                    Timber.d("showDataUsageWarningDialog: Coroutine launched for saveDataUsageWarningAccepted")
                    dataStoreManager.saveDataUsageWarningAccepted(true)
                    Timber.d("showDataUsageWarningDialog: saveDataUsageWarningAccepted completed")
                }
                Timber.d("showDataUsageWarningDialog: Dialog dismissed after Accept and Continue click")
                dialog.dismiss()
            }
            .setCancelable(false)
            .create()
        Timber.d("showDataUsageWarningDialog: Dialog created, about to show")
        dialog.show()
        Timber.d("showDataUsageWarningDialog: Dialog shown")
    }
}
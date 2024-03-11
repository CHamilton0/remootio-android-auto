package chamilton0.remootioandroidws.main

import android.app.Service
import android.content.Intent
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.IBinder
import android.util.Log


class NetworkService : Service() {
    private val networkCallback: NetworkCallback = object : NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            checkWifiStatus()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.d(TAG, "Network lost")
            // Place your logic when the network connection is lost
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (!isServiceRunning) {
            Log.d(TAG, "Service started")
            isServiceRunning = true
            registerNetworkCallback()
        } else {
            Log.d(TAG, "Service is already running")
        }
        // Return START_STICKY to ensure the service restarts if it's killed by the system
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        unregisterNetworkCallback()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    private fun registerNetworkCallback() {
        val connManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        connManager.registerNetworkCallback(request, networkCallback)
    }

    private fun unregisterNetworkCallback() {
        val connManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        connManager.unregisterNetworkCallback(networkCallback)
    }

    private fun checkWifiStatus() {
        val connManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connManager.activeNetwork
        if (network != null) {
            val capabilities = connManager.getNetworkCapabilities(network)
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
                val wifiInfo = wifiManager.connectionInfo
                Log.d(TAG, "Connected to WiFi")
                // Place your WiFi-connected logic here
            } else {
                Log.d(TAG, "Connected, but not to WiFi")
                // Place your logic for being connected but not to WiFi here
            }
        } else {
            Log.d(TAG, "Not connected to any network")
            // Place your logic for not being connected to any network here
        }
    }

    companion object {
        private const val TAG = "MyBackgroundService"
        private var isServiceRunning = false
    }
}
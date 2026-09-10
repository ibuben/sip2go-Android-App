package uz.ex.sip2go.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Tracks the system default validated network — the right signal for LTE ↔ Wi‑Fi handoff. */
class NetworkMonitor(
    context: Context,
    private val onNetworkAvailable: () -> Unit,
    private val onNetworkLost: () -> Unit = {},
) {
    private val connectivityManager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var registered = false
    private var callback: ConnectivityManager.NetworkCallback? = null

    @Volatile
    var validatedNetwork: Network? = null
        private set

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            considerNetwork(network, forceNotify = true)
        }

        override fun onLost(network: Network) {
            if (validatedNetwork == network) {
                validatedNetwork = null
            }
            val replacement = findValidatedNetwork()
            if (replacement != null) {
                validatedNetwork = replacement
                notifyAvailable()
            } else {
                notifyLost()
            }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            if (isValidated(caps)) {
                considerNetwork(network, forceNotify = false)
            } else if (validatedNetwork == network) {
                validatedNetwork = findValidatedNetwork()
                if (validatedNetwork == null) {
                    notifyLost()
                } else {
                    notifyAvailable()
                }
            }
        }
    }

    private fun considerNetwork(network: Network, forceNotify: Boolean) {
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return
        if (!isValidated(caps)) {
            return
        }
        val changed = forceNotify || validatedNetwork == null || validatedNetwork != network
        validatedNetwork = network
        if (changed) {
            notifyAvailable()
        }
    }

    private fun notifyAvailable() {
        mainHandler.post { onNetworkAvailable() }
    }

    private fun notifyLost() {
        mainHandler.post { onNetworkLost() }
    }

    fun start() {
        if (registered) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
        }
        callback = networkCallback
        registered = true
        validatedNetwork = findValidatedNetwork()
    }

    fun stop() {
        if (!registered) return
        callback?.let { connectivityManager.unregisterNetworkCallback(it) }
        callback = null
        registered = false
        validatedNetwork = null
    }

    fun hasInternet(): Boolean = findValidatedNetwork() != null

    fun preferredNetwork(): Network? = validatedNetwork ?: findValidatedNetwork()

    private fun findValidatedNetwork(): Network? {
        validatedNetwork?.let { network ->
            val caps = connectivityManager.getNetworkCapabilities(network)
            if (caps != null && isValidated(caps)) {
                return network
            }
        }
        val active = connectivityManager.activeNetwork ?: return null
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return null
        return active.takeIf { isValidated(caps) }
    }

    private fun isValidated(caps: NetworkCapabilities): Boolean {
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

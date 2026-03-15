package com.druk.servicebrowser.ui.viewmodel

import android.app.Application
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.druk.servicebrowser.BonjourApplication
import com.druk.servicebrowser.BonjourServiceInfo
import com.druk.servicebrowser.Config
import com.druk.servicebrowser.ServiceTypeResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper

data class BonjourDomain(
    val info: BonjourServiceInfo,
    var serviceCount: Int = 0
) {
    // Delegate identity to the underlying BonjourServiceInfo
    val serviceName: String get() = info.displayName
    val regType: String? get() = info.regType
    val domain: String? get() = info.domain

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BonjourDomain) return false
        return info == other.info
    }

    override fun hashCode(): Int = info.hashCode()
}

class RegTypeBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val nsdManager = BonjourApplication.getNsdManager(application)
    private val resolver = ServiceTypeResolver()

    private val handler = Handler(Looper.getMainLooper())
    private val activeListeners = HashMap<String, NsdManager.DiscoveryListener>()
    private val foundTypes = HashMap<String, BonjourDomain>()
    private val retryChannel = Channel<String>(Channel.UNLIMITED)

    private val _services = MutableStateFlow<List<BonjourDomain>>(emptyList())
    val services: StateFlow<List<BonjourDomain>> = _services

    private val _error = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    val error: SharedFlow<Throwable> = _error

    // LiveData-style accessors for fragment compatibility
    fun getServicesLiveData() = _services
    fun getErrorLiveData() = _error
    fun getServices() = foundTypes

    override fun onCleared() {
        super.onCleared()
        handler.removeCallbacksAndMessages(null)
        for (listener in activeListeners.values) {
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (_: IllegalArgumentException) {
            }
        }
        activeListeners.clear()
        foundTypes.clear()
        retryChannel.close()
    }

    fun startDiscovery() {
        // Collect service types from the resolver
        viewModelScope.launch(Dispatchers.IO) {
            resolver.serviceTypes().collect { serviceType ->
                launch(Dispatchers.Main) {
                    if (serviceType !in activeListeners) {
                        startSingleDiscovery(serviceType)
                    }
                }
            }
        }

        // Process retry queue
        viewModelScope.launch(Dispatchers.Main) {
            for (type in retryChannel) {
                if (type in activeListeners) continue
                if (!startSingleDiscovery(type)) {
                    delay(RETRY_DELAY_MS)
                    retryChannel.trySend(type)
                }
            }
        }
    }

    private fun startSingleDiscovery(serviceType: String): Boolean {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Browsing: $regType")
            }

            override fun onServiceFound(nsdServiceInfo: NsdServiceInfo) {
                handleServiceEvent(serviceType, false)
            }

            override fun onServiceLost(nsdServiceInfo: NsdServiceInfo) {
                handleServiceEvent(serviceType, true)
            }

            override fun onDiscoveryStopped(regType: String) {}

            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                Log.w(TAG, "Browse failed for $regType: $errorCode")
                handler.post {
                    activeListeners.remove(serviceType)
                }
                if (errorCode == NsdManager.FAILURE_MAX_LIMIT) {
                    retryChannel.trySend(serviceType)
                }
            }

            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                Log.w(TAG, "Stop failed for $regType: $errorCode")
            }
        }

        activeListeners[serviceType] = listener

        return try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            true
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Failed to start browse for $serviceType", e)
            activeListeners.remove(serviceType)
            false
        }
    }

    private fun handleServiceEvent(serviceType: String, lost: Boolean) {
        handler.post {
            val parts = serviceType.split(".")
            if (parts.size < 2) return@post

            val serviceName = parts[0]
            val protocolSuffix = parts[1]

            val domain = foundTypes.getOrPut(serviceType) {
                val info = BonjourServiceInfo(
                    serviceName = serviceName,
                    regType = "$protocolSuffix.${Config.LOCAL_DOMAIN}",
                    domain = Config.EMPTY_DOMAIN
                )
                BonjourDomain(info)
            }

            if (lost) {
                domain.serviceCount = maxOf(0, domain.serviceCount - 1)
            } else {
                domain.serviceCount++
            }

            _services.value = ArrayList(foundTypes.values)
        }
    }

    companion object {
        private const val TAG = "RegTypeBrowserVM"
        private const val RETRY_DELAY_MS = 3000L
    }
}

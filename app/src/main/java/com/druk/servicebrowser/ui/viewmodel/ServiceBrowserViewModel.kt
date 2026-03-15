package com.druk.servicebrowser.ui.viewmodel

import android.app.Application
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.druk.servicebrowser.BonjourApplication
import com.druk.servicebrowser.BonjourServiceInfo
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

class ServiceBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private val nsdManager = BonjourApplication.getNsdManager(application)
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _serviceEvent = MutableSharedFlow<BonjourServiceInfo>(extraBufferCapacity = 64)
    val serviceEvent: SharedFlow<BonjourServiceInfo> = _serviceEvent

    private val _errorEvent = MutableSharedFlow<Throwable>(extraBufferCapacity = 1)
    val errorEvent: SharedFlow<Throwable> = _errorEvent

    // LiveData-style accessors for fragment compatibility
    fun getServiceEvent() = _serviceEvent
    fun getErrorEvent() = _errorEvent

    override fun onCleared() {
        super.onCleared()
        stopDiscovery()
    }

    @Suppress("UNUSED_PARAMETER")
    fun startDiscovery(serviceType: String?, domain: String?) {
        stopDiscovery()

        var type = serviceType
        if (type != null && type.endsWith(".")) {
            type = type.substring(0, type.length - 1)
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Discovery started: $regType")
            }

            override fun onServiceFound(nsdServiceInfo: NsdServiceInfo) {
                nsdManager.resolveService(nsdServiceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        val info = BonjourServiceInfo.fromNsdServiceInfo(serviceInfo, false)
                        _serviceEvent.tryEmit(info)
                    }
                })
            }

            override fun onServiceLost(nsdServiceInfo: NsdServiceInfo) {
                val info = BonjourServiceInfo(
                    serviceName = nsdServiceInfo.serviceName,
                    regType = nsdServiceInfo.serviceType,
                    isLost = true
                )
                _serviceEvent.tryEmit(info)
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: $serviceType error: $errorCode")
                _errorEvent.tryEmit(RuntimeException("Discovery failed for $serviceType (error $errorCode)"))
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: $serviceType error: $errorCode")
            }
        }

        discoveryListener = listener
        nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun stopDiscovery() {
        discoveryListener?.let { listener ->
            try {
                nsdManager.stopServiceDiscovery(listener)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Discovery already stopped", e)
            }
        }
        discoveryListener = null
    }

    companion object {
        private const val TAG = "ServiceBrowserVM"
    }
}

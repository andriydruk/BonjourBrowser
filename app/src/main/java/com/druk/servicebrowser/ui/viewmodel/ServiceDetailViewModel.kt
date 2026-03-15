package com.druk.servicebrowser.ui.viewmodel

import android.app.Application
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.druk.servicebrowser.BonjourApplication
import com.druk.servicebrowser.BonjourServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.concurrent.Executors

class ServiceDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val nsdManager = BonjourApplication.getNsdManager(application)
    private var serviceInfoCallback: Any? = null

    private val _serviceInfo = MutableStateFlow<BonjourServiceInfo?>(null)
    val serviceInfo: StateFlow<BonjourServiceInfo?> = _serviceInfo

    private val _httpUrl = MutableStateFlow<URL?>(null)
    val httpUrl: StateFlow<URL?> = _httpUrl

    override fun onCleared() {
        super.onCleared()
        if (Build.VERSION.SDK_INT >= 34 && serviceInfoCallback != null) {
            try {
                nsdManager.unregisterServiceInfoCallback(
                    serviceInfoCallback as NsdManager.ServiceInfoCallback
                )
            } catch (_: IllegalArgumentException) {
            }
        }
        serviceInfoCallback = null
    }

    fun resolve(service: BonjourServiceInfo) {
        val nsdServiceInfo = NsdServiceInfo().apply {
            serviceName = service.displayName
            serviceType = service.regType
        }

        if (Build.VERSION.SDK_INT >= 34) {
            resolveLive(nsdServiceInfo)
        } else {
            resolveOnce(nsdServiceInfo)
        }
    }

    private fun resolveLive(nsdServiceInfo: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT < 34) return

        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                Log.e(TAG, "ServiceInfoCallback registration failed: $errorCode")
            }

            override fun onServiceUpdated(nsdServiceInfo: NsdServiceInfo) {
                val info = BonjourServiceInfo.fromNsdServiceInfo(nsdServiceInfo, false)
                _serviceInfo.value = info
                checkHttpConnection(info)
            }

            override fun onServiceLost() {
                Log.d(TAG, "Service lost")
            }

            override fun onServiceInfoCallbackUnregistered() {
                Log.d(TAG, "ServiceInfoCallback unregistered")
            }
        }

        serviceInfoCallback = callback
        nsdManager.registerServiceInfoCallback(nsdServiceInfo, Executors.newSingleThreadExecutor(), callback)
    }

    private fun resolveOnce(nsdServiceInfo: NsdServiceInfo) {
        @Suppress("DEPRECATION")
        nsdManager.resolveService(nsdServiceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed: $errorCode")
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val info = BonjourServiceInfo.fromNsdServiceInfo(serviceInfo, false)
                _serviceInfo.value = info
                checkHttpConnection(info)
            }
        })
    }

    private fun checkHttpConnection(service: BonjourServiceInfo) {
        viewModelScope.launch {
            val url = withContext(Dispatchers.IO) {
                val urls = mutableListOf<URL>()
                for (inetAddress in service.inetAddresses) {
                    try {
                        urls.add(URL(HTTP_PROTOCOL, inetAddress.hostAddress, service.port, ""))
                    } catch (_: MalformedURLException) {
                    }
                    try {
                        urls.add(URL(HTTPS_PROTOCOL, inetAddress.hostAddress, service.port, ""))
                    } catch (_: MalformedURLException) {
                    }
                }
                urls.firstOrNull { checkURL(it) }
            }
            if (url != null) {
                _httpUrl.value = url
            }
        }
    }

    private fun checkURL(url: URL): Boolean {
        return try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.connect()
            connection.responseCode == HttpURLConnection.HTTP_OK
        } catch (_: IOException) {
            false
        }
    }

    companion object {
        private const val TAG = "ServiceDetailVM"
        private const val HTTP_PROTOCOL = "http"
        private const val HTTPS_PROTOCOL = "https"
    }
}

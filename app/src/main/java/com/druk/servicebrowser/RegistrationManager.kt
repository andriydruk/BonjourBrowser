package com.druk.servicebrowser

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class RegistrationManager(context: Context) {

    private val nsdManager = BonjourApplication.getNsdManager(context)
    private val registrations = HashMap<BonjourServiceInfo, NsdManager.RegistrationListener>()

    suspend fun register(bonjourServiceInfo: BonjourServiceInfo): BonjourServiceInfo =
        suspendCancellableCoroutine { cont ->
            val nsdServiceInfo = NsdServiceInfo().apply {
                serviceName = bonjourServiceInfo.displayName
                serviceType = bonjourServiceInfo.regType
                port = bonjourServiceInfo.port
                bonjourServiceInfo.txtRecords.forEach { (key, value) ->
                    setAttribute(key, value)
                }
            }

            val listener = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                    val registered = BonjourServiceInfo(
                        serviceName = serviceInfo.serviceName,
                        regType = bonjourServiceInfo.regType,
                        port = bonjourServiceInfo.port,
                        txtRecords = bonjourServiceInfo.txtRecords
                    )
                    registrations[registered] = this
                    cont.resume(registered)
                }

                override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Registration failed: $errorCode")
                    cont.resumeWithException(RegistrationException(errorCode))
                }

                override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                    Log.i(TAG, "Service unregistered: ${serviceInfo.serviceName}")
                }

                override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                    Log.e(TAG, "Unregistration failed: $errorCode")
                }
            }

            cont.invokeOnCancellation {
                try {
                    nsdManager.unregisterService(listener)
                } catch (_: IllegalArgumentException) {
                }
            }

            nsdManager.registerService(nsdServiceInfo, NsdManager.PROTOCOL_DNS_SD, listener)
        }

    fun unregister(service: BonjourServiceInfo) {
        registrations.remove(service)?.let { listener ->
            nsdManager.unregisterService(listener)
        }
    }

    fun getRegisteredServices(): List<BonjourServiceInfo> =
        registrations.keys.toList()

    class RegistrationException(val errorCode: Int) : Exception("Registration failed: $errorCode")

    companion object {
        private const val TAG = "RegistrationManager"
    }
}

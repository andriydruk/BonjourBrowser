package com.druk.servicebrowser

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NsdDiscoveryTest {

    private lateinit var nsdManager: NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private lateinit var uniqueServiceName: String

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        nsdManager = context.getSystemService(NsdManager::class.java)
        uniqueServiceName = "TestSvc-${UUID.randomUUID().toString().substring(0, 8)}"
    }

    @After
    fun tearDown() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (_: IllegalArgumentException) {}
        }
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (_: IllegalArgumentException) {}
        }
    }

    @Test
    fun registerAndDiscover_roundTrip() {
        // Step 1: Register a service
        val registeredLatch = CountDownLatch(1)
        val registeredName = AtomicReference<String>()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = uniqueServiceName
            serviceType = SERVICE_TYPE
            port = SERVICE_PORT
            setAttribute("testKey", "testValue")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredName.set(info.serviceName)
                registeredLatch.countDown()
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                fail("Registration failed with error: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        assertTrue("Service registration timed out", registeredLatch.await(TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS))

        val actualName = registeredName.get()
        assertNotNull(actualName)

        // Step 2: Discover our service
        val discoveredLatch = CountDownLatch(1)
        val discoveredInfo = AtomicReference<NsdServiceInfo>()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(info: NsdServiceInfo) {
                if (actualName == info.serviceName) {
                    discoveredInfo.set(info)
                    discoveredLatch.countDown()
                }
            }

            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                fail("Discovery failed with error: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        assertTrue("Service discovery timed out", discoveredLatch.await(TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS))

        val discovered = discoveredInfo.get()
        assertNotNull("Discovered service should not be null", discovered)
        assertEquals(actualName, discovered.serviceName)

        // Step 3: Resolve the discovered service
        val resolvedLatch = CountDownLatch(1)
        val resolvedInfo = AtomicReference<NsdServiceInfo>()

        nsdManager.resolveService(discovered, object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                fail("Resolve failed with error: $errorCode")
            }

            override fun onServiceResolved(info: NsdServiceInfo) {
                resolvedInfo.set(info)
                resolvedLatch.countDown()
            }
        })

        assertTrue("Service resolution timed out", resolvedLatch.await(TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS))

        val resolved = resolvedInfo.get()
        assertNotNull("Resolved service should not be null", resolved)
        assertEquals(actualName, resolved.serviceName)
        assertEquals(SERVICE_PORT, resolved.port)

        // Step 4: Convert to BonjourServiceInfo and verify
        val bonjourInfo = BonjourServiceInfo.fromNsdServiceInfo(resolved, false)

        assertEquals(actualName, bonjourInfo.displayName)
        assertEquals(SERVICE_PORT, bonjourInfo.port)
        assertNotNull(bonjourInfo.inetAddresses)
        assertTrue("Expected at least one address", bonjourInfo.inetAddresses.isNotEmpty())
    }

    @Test
    fun registerAndDiscover_serviceInfoCallback() {
        // Step 1: Register a service
        val registeredLatch = CountDownLatch(1)
        val registeredName = AtomicReference<String>()

        val serviceInfo = NsdServiceInfo().apply {
            serviceName = uniqueServiceName
            serviceType = SERVICE_TYPE
            port = SERVICE_PORT
            setAttribute("version", "42")
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                registeredName.set(info.serviceName)
                registeredLatch.countDown()
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                fail("Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        assertTrue("Registration timed out", registeredLatch.await(TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS))

        val actualName = registeredName.get()

        // Step 2: Use registerServiceInfoCallback (API 34+) to get live updates
        val callbackLatch = CountDownLatch(1)
        val callbackInfo = AtomicReference<NsdServiceInfo>()

        val lookupInfo = NsdServiceInfo().apply {
            serviceName = actualName
            serviceType = SERVICE_TYPE
        }

        val serviceInfoCallback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                fail("ServiceInfoCallback registration failed: $errorCode")
            }

            override fun onServiceUpdated(info: NsdServiceInfo) {
                callbackInfo.set(info)
                callbackLatch.countDown()
            }

            override fun onServiceLost() {}
            override fun onServiceInfoCallbackUnregistered() {}
        }

        nsdManager.registerServiceInfoCallback(lookupInfo, Runnable::run, serviceInfoCallback)

        try {
            assertTrue("ServiceInfoCallback timed out", callbackLatch.await(TIMEOUT_SECONDS.toLong(), TimeUnit.SECONDS))

            val result = callbackInfo.get()
            assertNotNull(result)
            assertEquals(actualName, result.serviceName)
            assertEquals(SERVICE_PORT, result.port)
            assertNotNull(result.hostAddresses)
            assertTrue("Expected addresses from callback", result.hostAddresses.isNotEmpty())

            // Verify TXT record round-trip
            val versionBytes = result.attributes["version"]
            assertNotNull("Expected 'version' TXT attribute", versionBytes)
            assertEquals("42", String(versionBytes!!))

            // Convert and verify
            val bonjourInfo = BonjourServiceInfo.fromNsdServiceInfo(result, false)
            assertEquals(actualName, bonjourInfo.displayName)
            assertEquals("42", bonjourInfo.txtRecords["version"])
            assertTrue(bonjourInfo.inetAddresses.isNotEmpty())
        } finally {
            nsdManager.unregisterServiceInfoCallback(serviceInfoCallback)
        }
    }

    companion object {
        private const val SERVICE_TYPE = "_http._tcp"
        private const val SERVICE_PORT = 8123
        private const val TIMEOUT_SECONDS = 15
    }
}

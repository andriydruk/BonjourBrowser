package com.druk.servicebrowser;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Integration test that registers a real NSD service on the device,
 * discovers it, resolves it, and verifies all fields round-trip correctly.
 */
@RunWith(AndroidJUnit4.class)
public class NsdDiscoveryTest {

    private static final String SERVICE_TYPE = "_http._tcp";
    private static final int SERVICE_PORT = 8123;
    private static final int TIMEOUT_SECONDS = 15;

    private NsdManager nsdManager;
    private NsdManager.RegistrationListener registrationListener;
    private NsdManager.DiscoveryListener discoveryListener;
    private String uniqueServiceName;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        nsdManager = context.getSystemService(NsdManager.class);
        // Use a UUID suffix so parallel test runs don't collide
        uniqueServiceName = "TestSvc-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @After
    public void tearDown() {
        if (discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (registrationListener != null) {
            try {
                nsdManager.unregisterService(registrationListener);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    @Test
    public void registerAndDiscover_roundTrip() throws Exception {
        // Step 1: Register a service
        CountDownLatch registeredLatch = new CountDownLatch(1);
        AtomicReference<String> registeredName = new AtomicReference<>();

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(uniqueServiceName);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(SERVICE_PORT);
        serviceInfo.setAttribute("testKey", "testValue");

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo info) {
                registeredName.set(info.getServiceName());
                registeredLatch.countDown();
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                fail("Registration failed with error: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo info) {
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {
            }
        };

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        assertTrue("Service registration timed out", registeredLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        // NsdManager may alter the name to avoid conflicts
        String actualName = registeredName.get();
        assertNotNull(actualName);

        // Step 2: Discover our service
        CountDownLatch discoveredLatch = new CountDownLatch(1);
        AtomicReference<NsdServiceInfo> discoveredInfo = new AtomicReference<>();

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo info) {
                if (actualName.equals(info.getServiceName())) {
                    discoveredInfo.set(info);
                    discoveredLatch.countDown();
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo info) {
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                fail("Discovery failed with error: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            }
        };

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        assertTrue("Service discovery timed out", discoveredLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        NsdServiceInfo discovered = discoveredInfo.get();
        assertNotNull("Discovered service should not be null", discovered);
        assertEquals(actualName, discovered.getServiceName());

        // Step 3: Resolve the discovered service
        CountDownLatch resolvedLatch = new CountDownLatch(1);
        AtomicReference<NsdServiceInfo> resolvedInfo = new AtomicReference<>();

        nsdManager.resolveService(discovered, new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                fail("Resolve failed with error: " + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo info) {
                resolvedInfo.set(info);
                resolvedLatch.countDown();
            }
        });

        assertTrue("Service resolution timed out", resolvedLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        NsdServiceInfo resolved = resolvedInfo.get();
        assertNotNull("Resolved service should not be null", resolved);
        assertEquals(actualName, resolved.getServiceName());
        assertEquals(SERVICE_PORT, resolved.getPort());

        // Step 4: Convert to BonjourServiceInfo and verify
        BonjourServiceInfo bonjourInfo = BonjourServiceInfo.fromNsdServiceInfo(resolved, false);

        assertEquals(actualName, bonjourInfo.getServiceName());
        assertEquals(SERVICE_PORT, bonjourInfo.getPort());
        assertNotNull(bonjourInfo.getInetAddresses());
        assertTrue("Expected at least one address", bonjourInfo.getInetAddresses().size() > 0);
    }

    @Test
    public void registerAndDiscover_serviceInfoCallback() throws Exception {
        // Step 1: Register a service
        CountDownLatch registeredLatch = new CountDownLatch(1);
        AtomicReference<String> registeredName = new AtomicReference<>();

        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(uniqueServiceName);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(SERVICE_PORT);
        serviceInfo.setAttribute("version", "42");

        registrationListener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo info) {
                registeredName.set(info.getServiceName());
                registeredLatch.countDown();
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
                fail("Registration failed: " + errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo info) {
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo info, int errorCode) {
            }
        };

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener);
        assertTrue("Registration timed out", registeredLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        String actualName = registeredName.get();

        // Step 2: Use registerServiceInfoCallback (API 34+) to get live updates
        CountDownLatch callbackLatch = new CountDownLatch(1);
        AtomicReference<NsdServiceInfo> callbackInfo = new AtomicReference<>();

        NsdServiceInfo lookupInfo = new NsdServiceInfo();
        lookupInfo.setServiceName(actualName);
        lookupInfo.setServiceType(SERVICE_TYPE);

        NsdManager.ServiceInfoCallback serviceInfoCallback = new NsdManager.ServiceInfoCallback() {
            @Override
            public void onServiceInfoCallbackRegistrationFailed(int errorCode) {
                fail("ServiceInfoCallback registration failed: " + errorCode);
            }

            @Override
            public void onServiceUpdated(NsdServiceInfo info) {
                callbackInfo.set(info);
                callbackLatch.countDown();
            }

            @Override
            public void onServiceLost() {
            }

            @Override
            public void onServiceInfoCallbackUnregistered() {
            }
        };

        nsdManager.registerServiceInfoCallback(lookupInfo, Runnable::run, serviceInfoCallback);

        try {
            assertTrue("ServiceInfoCallback timed out", callbackLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

            NsdServiceInfo result = callbackInfo.get();
            assertNotNull(result);
            assertEquals(actualName, result.getServiceName());
            assertEquals(SERVICE_PORT, result.getPort());
            assertNotNull(result.getHostAddresses());
            assertTrue("Expected addresses from callback", result.getHostAddresses().size() > 0);

            // Verify TXT record round-trip
            byte[] versionBytes = result.getAttributes().get("version");
            assertNotNull("Expected 'version' TXT attribute", versionBytes);
            assertEquals("42", new String(versionBytes));

            // Convert and verify
            BonjourServiceInfo bonjourInfo = BonjourServiceInfo.fromNsdServiceInfo(result, false);
            assertEquals(actualName, bonjourInfo.getServiceName());
            assertEquals("42", bonjourInfo.getTxtRecords().get("version"));
            assertTrue(bonjourInfo.getInetAddresses().size() > 0);
        } finally {
            nsdManager.unregisterServiceInfoCallback(serviceInfoCallback);
        }
    }
}

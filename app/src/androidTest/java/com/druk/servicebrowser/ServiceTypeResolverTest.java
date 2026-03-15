package com.druk.servicebrowser;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class ServiceTypeResolverTest {

    @Test
    public void buildPtrQuery_producesValidDnsPacket() {
        byte[] query = ServiceTypeResolver.buildPtrQuery("_services._dns-sd._udp.local");
        assertNotNull(query);
        assertTrue("Query too short", query.length > 12);
        // First two bytes are ID (0), next two are flags (0 for standard query)
        assertEquals(0, query[0]);
        assertEquals(0, query[1]);
        assertEquals(0, query[2]);
        assertEquals(0, query[3]);
        // QDCOUNT = 1
        assertEquals(0, query[4]);
        assertEquals(1, query[5]);
    }

    @Test
    public void extractServiceType_validTypes() {
        assertEquals("_http._tcp", ServiceTypeResolver.extractServiceType("_http._tcp.local"));
        assertEquals("_http._tcp", ServiceTypeResolver.extractServiceType("_http._tcp.local."));
        assertEquals("_ipp._udp", ServiceTypeResolver.extractServiceType("_ipp._udp.local"));
        assertEquals("_airplay._tcp", ServiceTypeResolver.extractServiceType("_airplay._tcp.local"));
    }

    @Test
    public void extractServiceType_invalidTypes() {
        assertNull(ServiceTypeResolver.extractServiceType(null));
        assertNull(ServiceTypeResolver.extractServiceType(""));
        assertNull(ServiceTypeResolver.extractServiceType("local"));
        assertNull(ServiceTypeResolver.extractServiceType("http._tcp.local")); // missing underscore
        assertNull(ServiceTypeResolver.extractServiceType("_http.invalid.local")); // not _tcp/_udp
    }

    @Test
    public void startAndStop_runsWithoutCrash() throws Exception {
        ServiceTypeResolver resolver = new ServiceTypeResolver();
        CopyOnWriteArraySet<String> found = new CopyOnWriteArraySet<>();
        CountDownLatch started = new CountDownLatch(1);

        Thread thread = new Thread(() -> {
            started.countDown();
            resolver.start(found::add);
        });
        thread.start();

        assertTrue("Resolver didn't start", started.await(2, TimeUnit.SECONDS));
        Thread.sleep(1500);
        resolver.stop();
        thread.join(3000);

        assertNotNull(found);
        // Can't assert specific types — network varies. Just verify no crash.
    }
}

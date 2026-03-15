package com.druk.servicebrowser

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

@RunWith(AndroidJUnit4::class)
class ServiceTypeResolverTest {

    @Test
    fun buildPtrQuery_producesValidDnsPacket() {
        val query = ServiceTypeResolver.buildPtrQuery("_services._dns-sd._udp.local")
        assertNotNull(query)
        assertTrue("Query too short", query.size > 12)
        // First two bytes are ID (0), next two are flags (0 for standard query)
        assertEquals(0, query[0].toInt())
        assertEquals(0, query[1].toInt())
        assertEquals(0, query[2].toInt())
        assertEquals(0, query[3].toInt())
        // QDCOUNT = 1
        assertEquals(0, query[4].toInt())
        assertEquals(1, query[5].toInt())
    }

    @Test
    fun extractServiceType_validTypes() {
        assertEquals("_http._tcp", ServiceTypeResolver.extractServiceType("_http._tcp.local"))
        assertEquals("_http._tcp", ServiceTypeResolver.extractServiceType("_http._tcp.local."))
        assertEquals("_ipp._udp", ServiceTypeResolver.extractServiceType("_ipp._udp.local"))
        assertEquals("_airplay._tcp", ServiceTypeResolver.extractServiceType("_airplay._tcp.local"))
    }

    @Test
    fun extractServiceType_invalidTypes() {
        assertNull(ServiceTypeResolver.extractServiceType(null))
        assertNull(ServiceTypeResolver.extractServiceType(""))
        assertNull(ServiceTypeResolver.extractServiceType("local"))
        assertNull(ServiceTypeResolver.extractServiceType("http._tcp.local")) // missing underscore
        assertNull(ServiceTypeResolver.extractServiceType("_http.invalid.local")) // not _tcp/_udp
    }

    @Test
    fun startAndStop_runsWithoutCrash() {
        val resolver = ServiceTypeResolver()
        val found = CopyOnWriteArraySet<String>()

        runBlocking {
            withTimeoutOrNull(3000L) {
                resolver.serviceTypes().collect { serviceType ->
                    found.add(serviceType)
                }
            }
        }

        assertNotNull(found)
        // Can't assert specific types -- network varies. Just verify no crash.
    }
}

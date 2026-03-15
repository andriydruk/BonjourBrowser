package com.druk.servicebrowser

import android.net.nsd.NsdServiceInfo
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress

@RunWith(AndroidJUnit4::class)
class BonjourServiceInfoTest {

    @Test
    fun builder_createsCorrectInstance() {
        val info = BonjourServiceInfo.Builder()
            .serviceName("MyPrinter")
            .regType("_ipp._tcp")
            .domain("local.")
            .hostname("printer.local")
            .port(631)
            .txtRecord("rp", "ipp/print")
            .txtRecord("ty", "Brother HL-L2350DW")
            .ifIndex(3)
            .build()

        assertEquals("MyPrinter", info.displayName)
        assertEquals("_ipp._tcp", info.regType)
        assertEquals("local.", info.domain)
        assertEquals("printer.local", info.hostname)
        assertEquals(631, info.port)
        assertEquals(2, info.txtRecords.size)
        assertEquals("ipp/print", info.txtRecords["rp"])
        assertEquals("Brother HL-L2350DW", info.txtRecords["ty"])
        assertFalse(info.isLost)
        assertEquals(3, info.ifIndex)
    }

    @Test
    fun builder_lostFlag() {
        val info = BonjourServiceInfo.Builder()
            .serviceName("Test")
            .lost(true)
            .build()

        assertTrue(info.isLost)
    }

    @Test
    fun builder_copyConstructor() {
        val original = BonjourServiceInfo.Builder()
            .serviceName("Test")
            .regType("_http._tcp")
            .domain("local.")
            .port(8080)
            .txtRecord("path", "/index.html")
            .build()

        val copy = BonjourServiceInfo.Builder(original).build()

        assertEquals(original.displayName, copy.displayName)
        assertEquals(original.regType, copy.regType)
        assertEquals(original.domain, copy.domain)
        assertEquals(original.port, copy.port)
        assertEquals(original.txtRecords, copy.txtRecords)
        assertEquals(original, copy)
    }

    @Test
    fun parcelable_roundTrip() {
        val original = BonjourServiceInfo.Builder()
            .serviceName("WebServer")
            .regType("_http._tcp")
            .domain("local.")
            .hostname("myhost.local")
            .port(8080)
            .addAddress(InetAddress.getByName("192.168.1.100"))
            .addAddress(InetAddress.getByName("::1"))
            .txtRecord("path", "/")
            .txtRecord("tls", "true")
            .lost(false)
            .ifIndex(5)
            .build()

        val parcel = Parcel.obtain()
        parcel.writeParcelable(original, 0)
        parcel.setDataPosition(0)

        val restored = parcel.readParcelable<BonjourServiceInfo>(BonjourServiceInfo::class.java.classLoader)!!
        parcel.recycle()

        assertEquals(original.displayName, restored.displayName)
        assertEquals(original.regType, restored.regType)
        assertEquals(original.domain, restored.domain)
        assertEquals(original.hostname, restored.hostname)
        assertEquals(original.port, restored.port)
        assertEquals(original.isLost, restored.isLost)
        assertEquals(original.ifIndex, restored.ifIndex)
        assertEquals(2, restored.inetAddresses.size)
        assertEquals(2, restored.txtRecords.size)
        assertEquals("/", restored.txtRecords["path"])
        assertEquals("true", restored.txtRecords["tls"])
        assertNotNull(restored.inet4Address)
        assertNotNull(restored.inet6Address)
        assertEquals("192.168.1.100", restored.inet4Address!!.hostAddress)
    }

    @Test
    fun parcelable_emptyFields() {
        val original = BonjourServiceInfo.Builder()
            .serviceName("Minimal")
            .build()

        val parcel = Parcel.obtain()
        parcel.writeParcelable(original, 0)
        parcel.setDataPosition(0)

        val restored = parcel.readParcelable<BonjourServiceInfo>(BonjourServiceInfo::class.java.classLoader)!!
        parcel.recycle()

        assertEquals("Minimal", restored.displayName)
        assertNull(restored.regType)
        assertNull(restored.domain)
        assertNull(restored.hostname)
        assertEquals(0, restored.port)
        assertTrue(restored.inetAddresses.isEmpty())
        assertTrue(restored.txtRecords.isEmpty())
    }

    @Test
    fun equalsAndHashCode_matchOnIdentityFields() {
        val a = BonjourServiceInfo.Builder()
            .serviceName("Printer")
            .regType("_ipp._tcp")
            .domain("local.")
            .ifIndex(1)
            .port(631)
            .build()

        val b = BonjourServiceInfo.Builder()
            .serviceName("Printer")
            .regType("_ipp._tcp")
            .domain("local.")
            .ifIndex(1)
            .port(9100)  // different port -- should still be equal
            .build()

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun equalsAndHashCode_differOnIdentityFields() {
        val a = BonjourServiceInfo.Builder()
            .serviceName("Printer")
            .regType("_ipp._tcp")
            .domain("local.")
            .build()

        val b = BonjourServiceInfo.Builder()
            .serviceName("Scanner")
            .regType("_ipp._tcp")
            .domain("local.")
            .build()

        assertNotEquals(a, b)
    }

    @Test
    fun inetAddressConvenience_returnsCorrectTypes() {
        val info = BonjourServiceInfo.Builder()
            .serviceName("Test")
            .addAddress(InetAddress.getByName("10.0.0.1"))
            .addAddress(InetAddress.getByName("fe80::1"))
            .build()

        assertNotNull(info.inet4Address)
        assertNotNull(info.inet6Address)
        assertEquals("10.0.0.1", info.inet4Address!!.hostAddress)
        assertEquals("fe80::1", info.inet6Address!!.hostAddress)
    }

    @Test
    fun inetAddressConvenience_returnsNullWhenMissing() {
        val info = BonjourServiceInfo.Builder()
            .serviceName("Test")
            .build()

        assertNull(info.inet4Address)
        assertNull(info.inet6Address)
    }

    @Test
    fun fromNsdServiceInfo_convertsCorrectly() {
        val nsd = NsdServiceInfo().apply {
            serviceName = "MyWeb"
            serviceType = "_http._tcp."
            port = 80
            setAttribute("path", "/index.html")
        }

        val info = BonjourServiceInfo.fromNsdServiceInfo(nsd, false)

        assertEquals("MyWeb", info.displayName)
        assertEquals("_http._tcp", info.regType) // trailing dot stripped
        assertEquals(80, info.port)
        assertFalse(info.isLost)
        assertEquals("/index.html", info.txtRecords["path"])
    }

    @Test
    fun fromNsdServiceInfo_lostFlag() {
        val nsd = NsdServiceInfo().apply {
            serviceName = "Gone"
            serviceType = "_http._tcp"
        }

        val info = BonjourServiceInfo.fromNsdServiceInfo(nsd, true)

        assertTrue(info.isLost)
    }

    @Test
    fun getServiceName_neverNull() {
        val info = BonjourServiceInfo.Builder().build()
        assertNotNull(info.displayName)
        assertEquals("", info.displayName)
    }
}

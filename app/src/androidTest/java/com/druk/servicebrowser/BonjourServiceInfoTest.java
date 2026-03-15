package com.druk.servicebrowser;

import android.net.nsd.NsdServiceInfo;
import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetAddress;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class BonjourServiceInfoTest {

    @Test
    public void builder_createsCorrectInstance() {
        BonjourServiceInfo info = new BonjourServiceInfo.Builder()
                .serviceName("MyPrinter")
                .regType("_ipp._tcp")
                .domain("local.")
                .hostname("printer.local")
                .port(631)
                .txtRecord("rp", "ipp/print")
                .txtRecord("ty", "Brother HL-L2350DW")
                .ifIndex(3)
                .build();

        assertEquals("MyPrinter", info.getServiceName());
        assertEquals("_ipp._tcp", info.getRegType());
        assertEquals("local.", info.getDomain());
        assertEquals("printer.local", info.getHostname());
        assertEquals(631, info.getPort());
        assertEquals(2, info.getTxtRecords().size());
        assertEquals("ipp/print", info.getTxtRecords().get("rp"));
        assertEquals("Brother HL-L2350DW", info.getTxtRecords().get("ty"));
        assertFalse(info.isLost());
        assertEquals(3, info.getIfIndex());
    }

    @Test
    public void builder_lostFlag() {
        BonjourServiceInfo info = new BonjourServiceInfo.Builder()
                .serviceName("Test")
                .lost(true)
                .build();

        assertTrue(info.isLost());
    }

    @Test
    public void builder_copyConstructor() {
        BonjourServiceInfo original = new BonjourServiceInfo.Builder()
                .serviceName("Test")
                .regType("_http._tcp")
                .domain("local.")
                .port(8080)
                .txtRecord("path", "/index.html")
                .build();

        BonjourServiceInfo copy = new BonjourServiceInfo.Builder(original).build();

        assertEquals(original.getServiceName(), copy.getServiceName());
        assertEquals(original.getRegType(), copy.getRegType());
        assertEquals(original.getDomain(), copy.getDomain());
        assertEquals(original.getPort(), copy.getPort());
        assertEquals(original.getTxtRecords(), copy.getTxtRecords());
        assertEquals(original, copy);
    }

    @Test
    public void parcelable_roundTrip() throws Exception {
        BonjourServiceInfo original = new BonjourServiceInfo.Builder()
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
                .build();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BonjourServiceInfo restored = BonjourServiceInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals(original.getServiceName(), restored.getServiceName());
        assertEquals(original.getRegType(), restored.getRegType());
        assertEquals(original.getDomain(), restored.getDomain());
        assertEquals(original.getHostname(), restored.getHostname());
        assertEquals(original.getPort(), restored.getPort());
        assertEquals(original.isLost(), restored.isLost());
        assertEquals(original.getIfIndex(), restored.getIfIndex());
        assertEquals(2, restored.getInetAddresses().size());
        assertEquals(2, restored.getTxtRecords().size());
        assertEquals("/", restored.getTxtRecords().get("path"));
        assertEquals("true", restored.getTxtRecords().get("tls"));
        assertNotNull(restored.getInet4Address());
        assertNotNull(restored.getInet6Address());
        assertEquals("192.168.1.100", restored.getInet4Address().getHostAddress());
    }

    @Test
    public void parcelable_emptyFields() {
        BonjourServiceInfo original = new BonjourServiceInfo.Builder()
                .serviceName("Minimal")
                .build();

        Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);

        BonjourServiceInfo restored = BonjourServiceInfo.CREATOR.createFromParcel(parcel);
        parcel.recycle();

        assertEquals("Minimal", restored.getServiceName());
        assertNull(restored.getRegType());
        assertNull(restored.getDomain());
        assertNull(restored.getHostname());
        assertEquals(0, restored.getPort());
        assertTrue(restored.getInetAddresses().isEmpty());
        assertTrue(restored.getTxtRecords().isEmpty());
    }

    @Test
    public void equalsAndHashCode_matchOnIdentityFields() {
        BonjourServiceInfo a = new BonjourServiceInfo.Builder()
                .serviceName("Printer")
                .regType("_ipp._tcp")
                .domain("local.")
                .ifIndex(1)
                .port(631)
                .build();

        BonjourServiceInfo b = new BonjourServiceInfo.Builder()
                .serviceName("Printer")
                .regType("_ipp._tcp")
                .domain("local.")
                .ifIndex(1)
                .port(9100)  // different port — should still be equal
                .build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equalsAndHashCode_differOnIdentityFields() {
        BonjourServiceInfo a = new BonjourServiceInfo.Builder()
                .serviceName("Printer")
                .regType("_ipp._tcp")
                .domain("local.")
                .build();

        BonjourServiceInfo b = new BonjourServiceInfo.Builder()
                .serviceName("Scanner")
                .regType("_ipp._tcp")
                .domain("local.")
                .build();

        assertNotEquals(a, b);
    }

    @Test
    public void inetAddressConvenience_returnsCorrectTypes() throws Exception {
        BonjourServiceInfo info = new BonjourServiceInfo.Builder()
                .serviceName("Test")
                .addAddress(InetAddress.getByName("10.0.0.1"))
                .addAddress(InetAddress.getByName("fe80::1"))
                .build();

        assertNotNull(info.getInet4Address());
        assertNotNull(info.getInet6Address());
        assertEquals("10.0.0.1", info.getInet4Address().getHostAddress());
        assertEquals("fe80::1", info.getInet6Address().getHostAddress());
    }

    @Test
    public void inetAddressConvenience_returnsNullWhenMissing() {
        BonjourServiceInfo info = new BonjourServiceInfo.Builder()
                .serviceName("Test")
                .build();

        assertNull(info.getInet4Address());
        assertNull(info.getInet6Address());
    }

    @Test
    public void fromNsdServiceInfo_convertsCorrectly() throws Exception {
        NsdServiceInfo nsd = new NsdServiceInfo();
        nsd.setServiceName("MyWeb");
        nsd.setServiceType("_http._tcp.");
        nsd.setPort(80);
        nsd.setAttribute("path", "/index.html");

        BonjourServiceInfo info = BonjourServiceInfo.fromNsdServiceInfo(nsd, false);

        assertEquals("MyWeb", info.getServiceName());
        assertEquals("_http._tcp", info.getRegType()); // trailing dot stripped
        assertEquals(80, info.getPort());
        assertFalse(info.isLost());
        assertEquals("/index.html", info.getTxtRecords().get("path"));
    }

    @Test
    public void fromNsdServiceInfo_lostFlag() {
        NsdServiceInfo nsd = new NsdServiceInfo();
        nsd.setServiceName("Gone");
        nsd.setServiceType("_http._tcp");

        BonjourServiceInfo info = BonjourServiceInfo.fromNsdServiceInfo(nsd, true);

        assertTrue(info.isLost());
    }

    @Test
    public void getServiceName_neverNull() {
        BonjourServiceInfo info = new BonjourServiceInfo.Builder().build();
        assertNotNull(info.getServiceName());
        assertEquals("", info.getServiceName());
    }
}

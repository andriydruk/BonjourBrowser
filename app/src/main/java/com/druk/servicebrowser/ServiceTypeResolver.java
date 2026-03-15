package com.druk.servicebrowser;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/**
 * Continuously listens for mDNS service type announcements on the local network.
 * <p>
 * NsdManager does not support the _services._dns-sd._udp.local meta-query, so we
 * join the mDNS multicast groups (IPv4 224.0.0.251 and IPv6 ff02::fb, port 5353),
 * send PTR queries, and deliver each discovered type immediately via callback.
 * <p>
 * The listener runs until {@link #stop()} is called.
 */
public class ServiceTypeResolver {

    private static final String TAG = "ServiceTypeResolver";
    private static final String MDNS_IPV4_ADDRESS = "224.0.0.251";
    private static final String MDNS_IPV6_ADDRESS = "ff02::fb";
    private static final int MDNS_PORT = 5353;

    /** How often to re-send the PTR query to solicit responses from new devices. */
    private static final long QUERY_INTERVAL_MS = 15000;

    private static final int TYPE_PTR = 12;
    private static final int CLASS_IN = 1;

    public interface Callback {
        void onServiceTypeFound(String serviceType);
    }

    private volatile boolean running = false;
    private MulticastSocket socket;

    /**
     * Start listening on a background thread. Calls back on that thread for each
     * new service type discovered. Blocks until {@link #stop()} is called.
     */
    public void start(Callback callback) {
        running = true;
        Set<String> seen = new HashSet<>();
        byte[] query = buildPtrQuery("_services._dns-sd._udp.local");

        NetworkInterface wlanInterface = findMulticastInterface();
        if (wlanInterface == null) {
            Log.e(TAG, "No suitable multicast interface found");
            return;
        }

        InetAddress ipv4Group;
        InetAddress ipv6Group;
        try {
            ipv4Group = InetAddress.getByName(MDNS_IPV4_ADDRESS);
            ipv6Group = InetAddress.getByName(MDNS_IPV6_ADDRESS);
        } catch (IOException e) {
            Log.e(TAG, "Failed to resolve mDNS multicast addresses", e);
            return;
        }

        try {
            socket = new MulticastSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(MDNS_PORT));
            socket.setNetworkInterface(wlanInterface);
            socket.setSoTimeout(1000);
            socket.setTimeToLive(255);

            // Join both multicast groups
            boolean hasIpv4 = false;
            boolean hasIpv6 = false;
            Enumeration<InetAddress> addrs = wlanInterface.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (addr instanceof Inet4Address) hasIpv4 = true;
                if (addr instanceof Inet6Address) hasIpv6 = true;
            }

            if (hasIpv4) {
                socket.joinGroup(new InetSocketAddress(ipv4Group, MDNS_PORT), wlanInterface);
            }
            if (hasIpv6) {
                try {
                    socket.joinGroup(new InetSocketAddress(ipv6Group, MDNS_PORT), wlanInterface);
                } catch (IOException e) {
                    Log.w(TAG, "Failed to join IPv6 multicast: " + e.getMessage());
                }
            }

            Log.i(TAG, "Listening on " + wlanInterface.getName()
                    + " (IPv4=" + hasIpv4 + ", IPv6=" + hasIpv6 + ")");

            // Send initial query
            sendQuery(socket, query, ipv4Group, hasIpv4 ? ipv6Group : null);
            long lastQueryTime = System.currentTimeMillis();

            byte[] buf = new byte[4096];
            while (running) {
                try {
                    DatagramPacket response = new DatagramPacket(buf, buf.length);
                    socket.receive(response);
                    parseResponse(buf, response.getLength(), seen, callback);
                } catch (SocketTimeoutException ignored) {
                }

                // Re-send query periodically to pick up new devices
                if (System.currentTimeMillis() - lastQueryTime > QUERY_INTERVAL_MS) {
                    sendQuery(socket, query, ipv4Group, hasIpv6 ? ipv6Group : null);
                    lastQueryTime = System.currentTimeMillis();
                }
            }

        } catch (IOException e) {
            if (running) {
                Log.e(TAG, "Socket error", e);
            }
        } finally {
            closeSocket();
        }
    }

    public void stop() {
        running = false;
        closeSocket();
    }

    private void closeSocket() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    private static void sendQuery(MulticastSocket socket, byte[] query,
                                  InetAddress ipv4Group, InetAddress ipv6Group) {
        try {
            socket.send(new DatagramPacket(query, query.length, ipv4Group, MDNS_PORT));
        } catch (IOException e) {
            Log.w(TAG, "Failed to send IPv4 query: " + e.getMessage());
        }
        if (ipv6Group != null) {
            try {
                socket.send(new DatagramPacket(query, query.length, ipv6Group, MDNS_PORT));
            } catch (IOException e) {
                Log.w(TAG, "Failed to send IPv6 query: " + e.getMessage());
            }
        }
    }

    private static NetworkInterface findMulticastInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp() || !ni.supportsMulticast()) continue;
                return ni;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to enumerate interfaces", e);
        }
        return null;
    }

    static byte[] buildPtrQuery(String name) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);

            dos.writeShort(0);      // ID
            dos.writeShort(0);      // Flags
            dos.writeShort(1);      // QDCOUNT
            dos.writeShort(0);      // ANCOUNT
            dos.writeShort(0);      // NSCOUNT
            dos.writeShort(0);      // ARCOUNT

            writeDnsName(dos, name);
            dos.writeShort(TYPE_PTR);
            dos.writeShort(CLASS_IN);

            dos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Failed to build DNS query", e);
        }
    }

    private static void writeDnsName(DataOutputStream dos, String name) throws IOException {
        if (name.endsWith(".")) {
            name = name.substring(0, name.length() - 1);
        }
        for (String label : name.split("\\.")) {
            byte[] bytes = label.getBytes("UTF-8");
            dos.writeByte(bytes.length);
            dos.write(bytes);
        }
        dos.writeByte(0);
    }

    /**
     * Parse a DNS response. For each new PTR service type, invoke the callback.
     */
    private static void parseResponse(byte[] data, int length, Set<String> seen, Callback callback) {
        if (length < 12) return;

        ByteBuffer buf = ByteBuffer.wrap(data, 0, length);

        buf.getShort(); // ID
        buf.getShort(); // Flags
        int qdCount = buf.getShort() & 0xFFFF;
        int anCount = buf.getShort() & 0xFFFF;
        int nsCount = buf.getShort() & 0xFFFF;
        int arCount = buf.getShort() & 0xFFFF;

        for (int i = 0; i < qdCount; i++) {
            skipDnsName(buf);
            if (buf.remaining() < 4) return;
            buf.getShort();
            buf.getShort();
        }

        int totalRecords = anCount + nsCount + arCount;
        for (int i = 0; i < totalRecords; i++) {
            if (buf.remaining() < 1) return;

            readDnsName(buf, data);
            if (buf.remaining() < 10) return;

            int type = buf.getShort() & 0xFFFF;
            buf.getShort(); // class
            buf.getInt();   // ttl
            int rdLength = buf.getShort() & 0xFFFF;

            if (buf.remaining() < rdLength) return;

            if (type == TYPE_PTR) {
                int rdStart = buf.position();
                String target = readDnsName(buf, data);
                buf.position(rdStart + rdLength);

                String serviceType = extractServiceType(target);
                if (serviceType != null && seen.add(serviceType)) {
                    Log.d(TAG, "Discovered: " + serviceType);
                    callback.onServiceTypeFound(serviceType);
                }
            } else {
                buf.position(buf.position() + rdLength);
            }
        }
    }

    static String extractServiceType(String target) {
        if (target == null) return null;

        if (target.endsWith(".")) {
            target = target.substring(0, target.length() - 1);
        }

        String[] parts = target.split("\\.");
        if (parts.length < 2) return null;

        String name = parts[0];
        String proto = parts[1];

        if (!name.startsWith("_")) return null;
        if (!"_tcp".equals(proto) && !"_udp".equals(proto)) return null;

        return name + "." + proto;
    }

    private static String readDnsName(ByteBuffer buf, byte[] data) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        int savedPos = -1;

        while (buf.remaining() > 0) {
            int len = buf.get() & 0xFF;
            if (len == 0) break;

            if ((len & 0xC0) == 0xC0) {
                if (buf.remaining() < 1) break;
                int offset = ((len & 0x3F) << 8) | (buf.get() & 0xFF);
                if (savedPos == -1) {
                    savedPos = buf.position();
                }
                buf.position(offset);
                continue;
            }

            if (buf.remaining() < len) break;

            if (!first) sb.append('.');
            byte[] labelBytes = new byte[len];
            buf.get(labelBytes);
            sb.append(new String(labelBytes));
            first = false;
        }

        if (savedPos != -1) {
            buf.position(savedPos);
        }

        return sb.toString();
    }

    private static void skipDnsName(ByteBuffer buf) {
        while (buf.remaining() > 0) {
            int len = buf.get() & 0xFF;
            if (len == 0) break;
            if ((len & 0xC0) == 0xC0) {
                buf.get();
                break;
            }
            if (buf.remaining() < len) break;
            buf.position(buf.position() + len);
        }
    }
}

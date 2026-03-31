package com.druk.servicebrowser

import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.nio.ByteBuffer

object MdnsUtils {

    private const val TAG = "MdnsUtils"

    const val MDNS_IPV4_ADDRESS = "224.0.0.251"
    const val MDNS_IPV6_ADDRESS = "ff02::fb"
    const val MDNS_PORT = 5353

    const val TYPE_PTR = 12
    const val TYPE_A = 1
    const val TYPE_AAAA = 28
    const val CLASS_IN = 1

    data class MulticastSocketInfo(
        val socket: MulticastSocket,
        val ipv4Group: InetAddress,
        val ipv6Group: InetAddress?,
        val networkInterface: NetworkInterface
    )

    fun openMulticastSocket(): MulticastSocketInfo? {
        val wlanInterface = findMulticastInterface()
        if (wlanInterface == null) {
            Log.e(TAG, "No suitable multicast interface found")
            return null
        }

        val ipv4Group: InetAddress
        val ipv6Group: InetAddress
        try {
            ipv4Group = InetAddress.getByName(MDNS_IPV4_ADDRESS)
            ipv6Group = InetAddress.getByName(MDNS_IPV6_ADDRESS)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to resolve mDNS multicast addresses", e)
            return null
        }

        val socket = MulticastSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(MDNS_PORT))
            networkInterface = wlanInterface
            soTimeout = 1000
            timeToLive = 255
        }

        var hasIpv4 = false
        var hasIpv6 = false
        val addrs = wlanInterface.inetAddresses
        while (addrs.hasMoreElements()) {
            when (addrs.nextElement()) {
                is Inet4Address -> hasIpv4 = true
                is Inet6Address -> hasIpv6 = true
            }
        }

        if (hasIpv4) {
            socket.joinGroup(InetSocketAddress(ipv4Group, MDNS_PORT), wlanInterface)
        }
        if (hasIpv6) {
            try {
                socket.joinGroup(InetSocketAddress(ipv6Group, MDNS_PORT), wlanInterface)
            } catch (e: IOException) {
                Log.w(TAG, "Failed to join IPv6 multicast: ${e.message}")
            }
        }

        return MulticastSocketInfo(
            socket = socket,
            ipv4Group = ipv4Group,
            ipv6Group = if (hasIpv6) ipv6Group else null,
            networkInterface = wlanInterface
        )
    }

    fun findMulticastInterface(): NetworkInterface? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp || !ni.supportsMulticast()) continue
                return ni
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to enumerate interfaces", e)
        }
        return null
    }

    fun buildQuery(name: String, type: Int): ByteArray {
        try {
            val baos = ByteArrayOutputStream()
            val dos = DataOutputStream(baos)

            dos.writeShort(0)      // ID
            dos.writeShort(0)      // Flags
            dos.writeShort(1)      // QDCOUNT
            dos.writeShort(0)      // ANCOUNT
            dos.writeShort(0)      // NSCOUNT
            dos.writeShort(0)      // ARCOUNT

            writeDnsName(dos, name)
            dos.writeShort(type)
            dos.writeShort(CLASS_IN)

            dos.flush()
            return baos.toByteArray()
        } catch (e: IOException) {
            throw RuntimeException("Failed to build DNS query", e)
        }
    }

    fun sendQuery(
        socket: MulticastSocket, query: ByteArray,
        ipv4Group: InetAddress, ipv6Group: InetAddress?
    ) {
        try {
            socket.send(DatagramPacket(query, query.size, ipv4Group, MDNS_PORT))
        } catch (e: IOException) {
            Log.w(TAG, "Failed to send IPv4 query: ${e.message}")
        }
        if (ipv6Group != null) {
            try {
                socket.send(DatagramPacket(query, query.size, ipv6Group, MDNS_PORT))
            } catch (e: IOException) {
                Log.w(TAG, "Failed to send IPv6 query: ${e.message}")
            }
        }
    }

    fun writeDnsName(dos: DataOutputStream, name: String) {
        var n = name
        if (n.endsWith(".")) {
            n = n.substring(0, n.length - 1)
        }
        for (label in n.split("\\.".toRegex())) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)
    }

    fun readDnsName(buf: ByteBuffer): String {
        val sb = StringBuilder()
        var first = true
        var savedPos = -1

        while (buf.remaining() > 0) {
            val len = buf.get().toInt() and 0xFF
            if (len == 0) break

            if ((len and 0xC0) == 0xC0) {
                if (buf.remaining() < 1) break
                val offset = ((len and 0x3F) shl 8) or (buf.get().toInt() and 0xFF)
                if (savedPos == -1) {
                    savedPos = buf.position()
                }
                buf.position(offset)
                continue
            }

            if (buf.remaining() < len) break

            if (!first) sb.append('.')
            val labelBytes = ByteArray(len)
            buf.get(labelBytes)
            sb.append(String(labelBytes))
            first = false
        }

        if (savedPos != -1) {
            buf.position(savedPos)
        }

        return sb.toString()
    }

    fun skipDnsName(buf: ByteBuffer) {
        while (buf.remaining() > 0) {
            val len = buf.get().toInt() and 0xFF
            if (len == 0) break
            if ((len and 0xC0) == 0xC0) {
                buf.get()
                break
            }
            if (buf.remaining() < len) break
            buf.position(buf.position() + len)
        }
    }
}

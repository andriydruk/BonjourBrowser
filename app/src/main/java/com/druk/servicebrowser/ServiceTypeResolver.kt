package com.druk.servicebrowser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
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
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

class ServiceTypeResolver {

    fun serviceTypes(): Flow<String> = callbackFlow {
        val seen = HashSet<String>()
        val query = buildPtrQuery("_services._dns-sd._udp.local")

        val wlanInterface = findMulticastInterface()
        if (wlanInterface == null) {
            Log.e(TAG, "No suitable multicast interface found")
            close()
            return@callbackFlow
        }

        val ipv4Group: InetAddress
        val ipv6Group: InetAddress
        try {
            ipv4Group = InetAddress.getByName(MDNS_IPV4_ADDRESS)
            ipv6Group = InetAddress.getByName(MDNS_IPV6_ADDRESS)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to resolve mDNS multicast addresses", e)
            close()
            return@callbackFlow
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

        Log.i(TAG, "Listening on ${wlanInterface.name} (IPv4=$hasIpv4, IPv6=$hasIpv6)")

        sendQuery(socket, query, ipv4Group, if (hasIpv4) ipv6Group else null)
        var lastQueryTime = System.currentTimeMillis()

        val buf = ByteArray(4096)
        try {
            while (isActive) {
                try {
                    val response = DatagramPacket(buf, buf.size)
                    socket.receive(response)
                    parseResponse(buf, response.length, seen) { serviceType ->
                        trySend(serviceType)
                    }
                } catch (_: SocketTimeoutException) {
                }

                if (System.currentTimeMillis() - lastQueryTime > QUERY_INTERVAL_MS) {
                    sendQuery(socket, query, ipv4Group, if (hasIpv6) ipv6Group else null)
                    lastQueryTime = System.currentTimeMillis()
                }
            }
        } catch (e: IOException) {
            if (isActive) {
                Log.e(TAG, "Socket error", e)
            }
        }

        awaitClose { socket.close() }
    }.flowOn(Dispatchers.IO)

    companion object {
        private const val TAG = "ServiceTypeResolver"
        private const val MDNS_IPV4_ADDRESS = "224.0.0.251"
        private const val MDNS_IPV6_ADDRESS = "ff02::fb"
        private const val MDNS_PORT = 5353
        private const val QUERY_INTERVAL_MS = 15000L
        private const val TYPE_PTR = 12
        private const val CLASS_IN = 1

        private fun sendQuery(
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

        private fun findMulticastInterface(): NetworkInterface? {
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

        fun buildPtrQuery(name: String): ByteArray {
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
                dos.writeShort(TYPE_PTR)
                dos.writeShort(CLASS_IN)

                dos.flush()
                return baos.toByteArray()
            } catch (e: IOException) {
                throw RuntimeException("Failed to build DNS query", e)
            }
        }

        private fun writeDnsName(dos: DataOutputStream, name: String) {
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

        private fun parseResponse(
            data: ByteArray, length: Int, seen: MutableSet<String>,
            onFound: (String) -> Unit
        ) {
            if (length < 12) return

            val buf = ByteBuffer.wrap(data, 0, length)

            buf.short // ID
            buf.short // Flags
            val qdCount = buf.short.toInt() and 0xFFFF
            val anCount = buf.short.toInt() and 0xFFFF
            val nsCount = buf.short.toInt() and 0xFFFF
            val arCount = buf.short.toInt() and 0xFFFF

            for (i in 0 until qdCount) {
                skipDnsName(buf)
                if (buf.remaining() < 4) return
                buf.short
                buf.short
            }

            val totalRecords = anCount + nsCount + arCount
            for (i in 0 until totalRecords) {
                if (buf.remaining() < 1) return

                readDnsName(buf)
                if (buf.remaining() < 10) return

                val type = buf.short.toInt() and 0xFFFF
                buf.short // class
                buf.int   // ttl
                val rdLength = buf.short.toInt() and 0xFFFF

                if (buf.remaining() < rdLength) return

                if (type == TYPE_PTR) {
                    val rdStart = buf.position()
                    val target = readDnsName(buf)
                    buf.position(rdStart + rdLength)

                    val serviceType = extractServiceType(target)
                    if (serviceType != null && seen.add(serviceType)) {
                        Log.d(TAG, "Discovered: $serviceType")
                        onFound(serviceType)
                    }
                } else {
                    buf.position(buf.position() + rdLength)
                }
            }
        }

        fun extractServiceType(target: String?): String? {
            if (target == null) return null

            var t = target
            if (t.endsWith(".")) {
                t = t.substring(0, t.length - 1)
            }

            val parts = t.split("\\.".toRegex())
            if (parts.size < 2) return null

            val name = parts[0]
            val proto = parts[1]

            if (!name.startsWith("_")) return null
            if (proto != "_tcp" && proto != "_udp") return null

            return "$name.$proto"
        }

        private fun readDnsName(buf: ByteBuffer): String {
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

        private fun skipDnsName(buf: ByteBuffer) {
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
}

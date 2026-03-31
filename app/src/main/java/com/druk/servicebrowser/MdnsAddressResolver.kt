package com.druk.servicebrowser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet6Address
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

object MdnsAddressResolver {

    private const val TAG = "MdnsAddressResolver"
    private const val DEFAULT_TIMEOUT_MS = 3000L

    suspend fun resolveAddresses(
        hostname: String,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS
    ): List<InetAddress> = withContext(Dispatchers.IO) {
        val fqdn = if (hostname.endsWith(".local")) hostname else "$hostname.local"

        val socketInfo = MdnsUtils.openMulticastSocket()
        if (socketInfo == null) {
            Log.e(TAG, "Failed to open multicast socket")
            return@withContext emptyList()
        }

        val socket = socketInfo.socket
        socket.soTimeout = 500

        val queryA = MdnsUtils.buildQuery(fqdn, MdnsUtils.TYPE_A)
        val queryAAAA = MdnsUtils.buildQuery(fqdn, MdnsUtils.TYPE_AAAA)

        val addresses = LinkedHashSet<InetAddress>()
        val buf = ByteArray(4096)

        try {
            MdnsUtils.sendQuery(socket, queryA, socketInfo.ipv4Group, socketInfo.ipv6Group)
            MdnsUtils.sendQuery(socket, queryAAAA, socketInfo.ipv4Group, socketInfo.ipv6Group)

            val startTime = System.currentTimeMillis()
            var resent = false

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val response = DatagramPacket(buf, buf.size)
                    socket.receive(response)
                    parseAddressResponse(buf, response.length, fqdn, socketInfo.networkInterface)
                        .forEach { addresses.add(it) }
                } catch (_: SocketTimeoutException) {
                }

                if (!resent && System.currentTimeMillis() - startTime > timeoutMs / 2) {
                    MdnsUtils.sendQuery(socket, queryA, socketInfo.ipv4Group, socketInfo.ipv6Group)
                    MdnsUtils.sendQuery(socket, queryAAAA, socketInfo.ipv4Group, socketInfo.ipv6Group)
                    resent = true
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Socket error during address resolution", e)
        } finally {
            socket.close()
        }

        Log.d(TAG, "Resolved $fqdn to ${addresses.size} address(es): $addresses")
        addresses.toList()
    }

    private fun parseAddressResponse(
        data: ByteArray,
        length: Int,
        queryName: String,
        networkInterface: java.net.NetworkInterface
    ): List<InetAddress> {
        if (length < 12) return emptyList()

        val buf = ByteBuffer.wrap(data, 0, length)
        val results = mutableListOf<InetAddress>()

        buf.short // ID
        buf.short // Flags
        val qdCount = buf.short.toInt() and 0xFFFF
        val anCount = buf.short.toInt() and 0xFFFF
        val nsCount = buf.short.toInt() and 0xFFFF
        val arCount = buf.short.toInt() and 0xFFFF

        for (i in 0 until qdCount) {
            MdnsUtils.skipDnsName(buf)
            if (buf.remaining() < 4) return results
            buf.short // type
            buf.short // class
        }

        val totalRecords = anCount + nsCount + arCount
        for (i in 0 until totalRecords) {
            if (buf.remaining() < 1) return results

            val name = MdnsUtils.readDnsName(buf)
            if (buf.remaining() < 10) return results

            val type = buf.short.toInt() and 0xFFFF
            buf.short // class
            buf.int   // TTL
            val rdLength = buf.short.toInt() and 0xFFFF

            if (buf.remaining() < rdLength) return results

            val nameMatches = name.equals(queryName, ignoreCase = true) ||
                    name.equals(queryName.removeSuffix("."), ignoreCase = true)

            if (nameMatches && type == MdnsUtils.TYPE_A && rdLength == 4) {
                val addrBytes = ByteArray(4)
                buf.get(addrBytes)
                try {
                    results.add(InetAddress.getByAddress(addrBytes))
                } catch (_: Exception) {
                }
            } else if (nameMatches && type == MdnsUtils.TYPE_AAAA && rdLength == 16) {
                val addrBytes = ByteArray(16)
                buf.get(addrBytes)
                try {
                    val addr = Inet6Address.getByAddress(null, addrBytes, networkInterface)
                    results.add(addr)
                } catch (_: Exception) {
                }
            } else {
                buf.position(buf.position() + rdLength)
            }
        }

        return results
    }
}

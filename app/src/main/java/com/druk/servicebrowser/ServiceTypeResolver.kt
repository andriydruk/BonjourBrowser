package com.druk.servicebrowser

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.IOException
import java.net.DatagramPacket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer

class ServiceTypeResolver {

    fun serviceTypes(): Flow<String> = callbackFlow {
        val seen = HashSet<String>()
        val query = MdnsUtils.buildQuery("_services._dns-sd._udp.local", MdnsUtils.TYPE_PTR)

        val socketInfo = MdnsUtils.openMulticastSocket()
        if (socketInfo == null) {
            close()
            return@callbackFlow
        }

        val socket = socketInfo.socket
        Log.i(TAG, "Listening on ${socketInfo.networkInterface.name}")

        MdnsUtils.sendQuery(socket, query, socketInfo.ipv4Group, socketInfo.ipv6Group)
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
                    MdnsUtils.sendQuery(socket, query, socketInfo.ipv4Group, socketInfo.ipv6Group)
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
        private const val QUERY_INTERVAL_MS = 15000L

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
                MdnsUtils.skipDnsName(buf)
                if (buf.remaining() < 4) return
                buf.short
                buf.short
            }

            val totalRecords = anCount + nsCount + arCount
            for (i in 0 until totalRecords) {
                if (buf.remaining() < 1) return

                MdnsUtils.readDnsName(buf)
                if (buf.remaining() < 10) return

                val type = buf.short.toInt() and 0xFFFF
                buf.short // class
                buf.int   // ttl
                val rdLength = buf.short.toInt() and 0xFFFF

                if (buf.remaining() < rdLength) return

                if (type == MdnsUtils.TYPE_PTR) {
                    val rdStart = buf.position()
                    val target = MdnsUtils.readDnsName(buf)
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
    }
}

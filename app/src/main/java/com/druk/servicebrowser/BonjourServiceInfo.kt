package com.druk.servicebrowser

import android.net.nsd.NsdServiceInfo
import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object InetAddressListParceler : Parceler<List<InetAddress>> {
    override fun create(parcel: Parcel): List<InetAddress> {
        val count = parcel.readInt()
        val addresses = ArrayList<InetAddress>(count)
        repeat(count) {
            val bytes = parcel.createByteArray()
            try {
                if (bytes != null) addresses.add(InetAddress.getByAddress(bytes))
            } catch (_: Exception) {
            }
        }
        return addresses
    }

    override fun List<InetAddress>.write(parcel: Parcel, flags: Int) {
        parcel.writeInt(size)
        for (addr in this) {
            parcel.writeByteArray(addr.address)
        }
    }
}

@Parcelize
@TypeParceler<List<InetAddress>, InetAddressListParceler>()
data class BonjourServiceInfo(
    val serviceName: String? = null,
    val regType: String? = null,
    val domain: String? = null,
    val hostname: String? = null,
    val port: Int = 0,
    val inetAddresses: List<InetAddress> = emptyList(),
    val txtRecords: Map<String, String> = emptyMap(),
    val isLost: Boolean = false,
    val ifIndex: Int = 0
) : Parcelable {

    /** serviceName, never null (empty string if null). */
    val displayName: String get() = serviceName ?: ""

    val inet4Address: Inet4Address?
        get() = inetAddresses.filterIsInstance<Inet4Address>().firstOrNull()

    val inet6Address: Inet6Address?
        get() = inetAddresses.filterIsInstance<Inet6Address>().firstOrNull()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BonjourServiceInfo) return false
        return ifIndex == other.ifIndex &&
                serviceName == other.serviceName &&
                regType == other.regType &&
                domain == other.domain
    }

    override fun hashCode(): Int {
        var result = serviceName?.hashCode() ?: 0
        result = 31 * result + (regType?.hashCode() ?: 0)
        result = 31 * result + (domain?.hashCode() ?: 0)
        result = 31 * result + ifIndex
        return result
    }

    override fun toString(): String =
        "BonjourServiceInfo(serviceName='$serviceName', regType='$regType', domain='$domain', port=$port, lost=$isLost)"

    companion object {
        fun fromNsdServiceInfo(nsdServiceInfo: NsdServiceInfo, lost: Boolean): BonjourServiceInfo {
            var serviceType = nsdServiceInfo.serviceType
            if (serviceType != null && serviceType.endsWith(".")) {
                serviceType = serviceType.substring(0, serviceType.length - 1)
            }

            val hostAddresses = nsdServiceInfo.hostAddresses
            val hostname = if (hostAddresses.isNotEmpty()) hostAddresses[0].hostName else null

            val txtRecords = mutableMapOf<String, String>()
            nsdServiceInfo.attributes?.forEach { (key, value) ->
                txtRecords[key] = if (value != null) String(value) else ""
            }

            return BonjourServiceInfo(
                serviceName = nsdServiceInfo.serviceName,
                regType = serviceType,
                domain = Config.LOCAL_DOMAIN,
                port = nsdServiceInfo.port,
                inetAddresses = hostAddresses.toList(),
                hostname = hostname,
                txtRecords = txtRecords,
                isLost = lost
            )
        }
    }

    /** Builder for backward compatibility with tests. */
    class Builder {
        private var serviceName: String? = null
        private var regType: String? = null
        private var domain: String? = null
        private var hostname: String? = null
        private var port: Int = 0
        private val inetAddresses = mutableListOf<InetAddress>()
        private val txtRecords = mutableMapOf<String, String>()
        private var lost: Boolean = false
        private var ifIndex: Int = 0

        constructor()

        constructor(info: BonjourServiceInfo) {
            serviceName = info.serviceName
            regType = info.regType
            domain = info.domain
            hostname = info.hostname
            port = info.port
            inetAddresses.addAll(info.inetAddresses)
            txtRecords.putAll(info.txtRecords)
            lost = info.isLost
            ifIndex = info.ifIndex
        }

        fun serviceName(serviceName: String?) = apply { this.serviceName = serviceName }
        fun regType(regType: String?) = apply { this.regType = regType }
        fun domain(domain: String?) = apply { this.domain = domain }
        fun hostname(hostname: String?) = apply { this.hostname = hostname }
        fun port(port: Int) = apply { this.port = port }
        fun addAddress(address: InetAddress) = apply { inetAddresses.add(address) }
        fun txtRecord(key: String, value: String) = apply { txtRecords[key] = value }
        fun txtRecords(records: Map<String, String>) = apply { txtRecords.putAll(records) }
        fun lost(lost: Boolean) = apply { this.lost = lost }
        fun ifIndex(ifIndex: Int) = apply { this.ifIndex = ifIndex }

        fun build() = BonjourServiceInfo(
            serviceName = serviceName,
            regType = regType,
            domain = domain,
            hostname = hostname,
            port = port,
            inetAddresses = inetAddresses.toList(),
            txtRecords = txtRecords.toMap(),
            isLost = lost,
            ifIndex = ifIndex
        )
    }
}

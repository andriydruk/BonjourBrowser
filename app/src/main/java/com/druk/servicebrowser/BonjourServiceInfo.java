package com.druk.servicebrowser;

import android.net.nsd.NsdServiceInfo;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BonjourServiceInfo implements Parcelable {

    private final String serviceName;
    private final String regType;
    private final String domain;
    private final String hostname;
    private final int port;
    private final List<InetAddress> inetAddresses;
    private final Map<String, String> txtRecords;
    private final boolean lost;
    private final int ifIndex;

    protected BonjourServiceInfo(Builder builder) {
        this.serviceName = builder.serviceName;
        this.regType = builder.regType;
        this.domain = builder.domain;
        this.hostname = builder.hostname;
        this.port = builder.port;
        this.inetAddresses = Collections.unmodifiableList(new ArrayList<>(builder.inetAddresses));
        this.txtRecords = Collections.unmodifiableMap(new HashMap<>(builder.txtRecords));
        this.lost = builder.lost;
        this.ifIndex = builder.ifIndex;
    }

    protected BonjourServiceInfo(Parcel in) {
        serviceName = in.readString();
        regType = in.readString();
        domain = in.readString();
        hostname = in.readString();
        port = in.readInt();
        lost = in.readByte() != 0;
        ifIndex = in.readInt();

        int addressCount = in.readInt();
        List<InetAddress> addresses = new ArrayList<>(addressCount);
        for (int i = 0; i < addressCount; i++) {
            byte[] addrBytes = in.createByteArray();
            try {
                addresses.add(InetAddress.getByAddress(addrBytes));
            } catch (Exception ignored) {
            }
        }
        inetAddresses = Collections.unmodifiableList(addresses);

        int recordCount = in.readInt();
        Map<String, String> records = new HashMap<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            String key = in.readString();
            String value = in.readString();
            records.put(key, value);
        }
        txtRecords = Collections.unmodifiableMap(records);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(serviceName);
        dest.writeString(regType);
        dest.writeString(domain);
        dest.writeString(hostname);
        dest.writeInt(port);
        dest.writeByte((byte) (lost ? 1 : 0));
        dest.writeInt(ifIndex);

        dest.writeInt(inetAddresses.size());
        for (InetAddress addr : inetAddresses) {
            dest.writeByteArray(addr.getAddress());
        }

        dest.writeInt(txtRecords.size());
        for (Map.Entry<String, String> entry : txtRecords.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeString(entry.getValue());
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BonjourServiceInfo> CREATOR = new Creator<BonjourServiceInfo>() {
        @Override
        public BonjourServiceInfo createFromParcel(Parcel in) {
            return new BonjourServiceInfo(in);
        }

        @Override
        public BonjourServiceInfo[] newArray(int size) {
            return new BonjourServiceInfo[size];
        }
    };

    public static BonjourServiceInfo fromNsdServiceInfo(@NonNull NsdServiceInfo nsdServiceInfo, boolean lost) {
        String serviceType = nsdServiceInfo.getServiceType();
        if (serviceType != null && serviceType.endsWith(".")) {
            serviceType = serviceType.substring(0, serviceType.length() - 1);
        }

        Builder builder = new Builder()
                .serviceName(nsdServiceInfo.getServiceName())
                .regType(serviceType)
                .port(nsdServiceInfo.getPort())
                .lost(lost);

        List<InetAddress> hostAddresses = nsdServiceInfo.getHostAddresses();
        if (hostAddresses != null) {
            for (InetAddress addr : hostAddresses) {
                builder.addAddress(addr);
            }
        }

        if (nsdServiceInfo.getHostAddresses() != null && !nsdServiceInfo.getHostAddresses().isEmpty()) {
            builder.hostname(nsdServiceInfo.getHostAddresses().get(0).getHostName());
        }

        Map<String, byte[]> attributes = nsdServiceInfo.getAttributes();
        if (attributes != null) {
            for (Map.Entry<String, byte[]> entry : attributes.entrySet()) {
                String value = entry.getValue() != null ? new String(entry.getValue()) : "";
                builder.txtRecord(entry.getKey(), value);
            }
        }

        return builder.build();
    }

    @NonNull
    public String getServiceName() {
        return serviceName != null ? serviceName : "";
    }

    @Nullable
    public String getRegType() {
        return regType;
    }

    @Nullable
    public String getDomain() {
        return domain;
    }

    @Nullable
    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    @NonNull
    public List<InetAddress> getInetAddresses() {
        return inetAddresses;
    }

    @NonNull
    public Map<String, String> getTxtRecords() {
        return txtRecords;
    }

    public boolean isLost() {
        return lost;
    }

    public int getIfIndex() {
        return ifIndex;
    }

    @Nullable
    public Inet4Address getInet4Address() {
        for (InetAddress addr : inetAddresses) {
            if (addr instanceof Inet4Address) {
                return (Inet4Address) addr;
            }
        }
        return null;
    }

    @Nullable
    public Inet6Address getInet6Address() {
        for (InetAddress addr : inetAddresses) {
            if (addr instanceof Inet6Address) {
                return (Inet6Address) addr;
            }
        }
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BonjourServiceInfo that = (BonjourServiceInfo) o;
        return ifIndex == that.ifIndex &&
                Objects.equals(serviceName, that.serviceName) &&
                Objects.equals(regType, that.regType) &&
                Objects.equals(domain, that.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceName, regType, domain, ifIndex);
    }

    @NonNull
    @Override
    public String toString() {
        return "BonjourServiceInfo{" +
                "serviceName='" + serviceName + '\'' +
                ", regType='" + regType + '\'' +
                ", domain='" + domain + '\'' +
                ", port=" + port +
                ", lost=" + lost +
                '}';
    }

    public static class Builder {
        private String serviceName;
        private String regType;
        private String domain;
        private String hostname;
        private int port;
        private final List<InetAddress> inetAddresses = new ArrayList<>();
        private final Map<String, String> txtRecords = new HashMap<>();
        private boolean lost;
        private int ifIndex;

        public Builder() {
        }

        public Builder(BonjourServiceInfo info) {
            this.serviceName = info.serviceName;
            this.regType = info.regType;
            this.domain = info.domain;
            this.hostname = info.hostname;
            this.port = info.port;
            this.inetAddresses.addAll(info.inetAddresses);
            this.txtRecords.putAll(info.txtRecords);
            this.lost = info.lost;
            this.ifIndex = info.ifIndex;
        }

        public Builder serviceName(String serviceName) {
            this.serviceName = serviceName;
            return this;
        }

        public Builder regType(String regType) {
            this.regType = regType;
            return this;
        }

        public Builder domain(String domain) {
            this.domain = domain;
            return this;
        }

        public Builder hostname(String hostname) {
            this.hostname = hostname;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder addAddress(InetAddress address) {
            this.inetAddresses.add(address);
            return this;
        }

        public Builder txtRecord(String key, String value) {
            this.txtRecords.put(key, value);
            return this;
        }

        public Builder txtRecords(Map<String, String> records) {
            this.txtRecords.putAll(records);
            return this;
        }

        public Builder lost(boolean lost) {
            this.lost = lost;
            return this;
        }

        public Builder ifIndex(int ifIndex) {
            this.ifIndex = ifIndex;
            return this;
        }

        public BonjourServiceInfo build() {
            return new BonjourServiceInfo(this);
        }
    }
}

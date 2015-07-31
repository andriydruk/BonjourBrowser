/*
 * Copyright (C) 2015 Andriy Druk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.druk.bonjour.browser.dnssd;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.util.ArrayMap;

import java.util.Map;
import java.util.regex.Pattern;

public class BonjourService implements Parcelable {

    public static final String DNS_RECORD_KEY_SERVICE_COUNT = "dns_record_key_service_count";
    public static final String DNS_RECORD_KEY_ADDRESS = "IP address";
    private static final String REG_TYPE_SEPARATOR = Pattern.quote(".");

    public final int flags;
    public final String serviceName;
    public final String regType;
    public final String domain;
    public Map<String, String> dnsRecords = new ArrayMap<>();
    public int ifIndex;
    public String hostname;
    public int port;

    public long timestamp = -1L;
    public boolean isDeleted = false;

    public BonjourService(int flags, int ifIndex, String serviceName, String regType, String domain){
        this.flags = flags;
        this.ifIndex = ifIndex;
        this.serviceName = serviceName;
        this.regType = regType;
        this.domain = domain;
    }

    public String[] getRegTypeParts(){
        return regType.split(REG_TYPE_SEPARATOR);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BonjourService that = (BonjourService) o;
        return serviceName.equals(that.serviceName) && regType.equals(that.regType) && domain.equals(that.domain);
    }

    @Override
    public int hashCode() {
        int result = serviceName.hashCode();
        result = 31 * result + regType.hashCode();
        result = 31 * result + domain.hashCode();
        return result;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.flags);
        dest.writeString(this.serviceName);
        dest.writeString(this.regType);
        dest.writeString(this.domain);
        writeMap(dest, this.dnsRecords);
        dest.writeInt(this.ifIndex);
        dest.writeString(this.hostname);
        dest.writeInt(this.port);
        dest.writeLong(this.timestamp);
    }

    protected BonjourService(Parcel in) {
        this.flags = in.readInt();
        this.serviceName = in.readString();
        this.regType = in.readString();
        this.domain = in.readString();
        this.dnsRecords = readMap(in);
        this.ifIndex = in.readInt();
        this.hostname = in.readString();
        this.port = in.readInt();
        this.timestamp = in.readLong();
    }

    public static final Parcelable.Creator<BonjourService> CREATOR = new Parcelable.Creator<BonjourService>() {
        public BonjourService createFromParcel(Parcel source) {
            return new BonjourService(source);
        }

        public BonjourService[] newArray(int size) {
            return new BonjourService[size];
        }
    };

    public static void writeMap(Parcel dest, Map<String, String> val) {
        if (val == null) {
            dest.writeInt(-1);
            return;
        }
        int N = val.size();
        dest.writeInt(N);
        for (String key : val.keySet()) {
            dest.writeString(key);
            dest.writeString(val.get(key));
        }
    }

    public final Map<String, String> readMap(Parcel in) {
        int N = in.readInt();
        if (N < 0){
            return null;
        }
        Map<String, String> result = new ArrayMap<>();
        for (int i = 0; i < N; i++) {
            result.put(in.readString(), in.readString());
        }
        return result;
    }

    @Override
    public String toString() {
        return "BonjourService{" +
                "serviceName='" + serviceName + '\'' +
                ", regType='" + regType + '\'' +
                ", domain='" + domain + '\'' +
                '}';
    }
}

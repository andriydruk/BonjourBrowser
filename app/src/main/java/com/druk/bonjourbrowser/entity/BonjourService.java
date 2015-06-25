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
package com.druk.bonjourbrowser.entity;

import android.support.v4.util.ArrayMap;

import java.util.regex.Pattern;

public class BonjourService {

    public static final String DNS_RECORD_KEY_SERVICE_COUNT = "count";
    public static final String DNS_RECORD_KEY_ADDRESS = "address";
    private static final String SEPARATOR = Pattern.quote(".");

    public final int flags;
    public final String serviceName;
    public final String regType;
    public final String domain;
    public final ArrayMap<String, String> dnsRecords = new ArrayMap<>();
    public int ifIndex;
    //TODO: change to RegType description
    public String fullServiceName;
    public String hostname;
    public int port;

    public long timestamp = -1L;

    public BonjourService(int flags, int ifIndex, String serviceName, String regType, String domain){
        this.flags = flags;
        this.ifIndex = ifIndex;
        this.serviceName = serviceName;
        this.regType = regType;
        this.domain = domain;
    }

    public String[] getRegTypeParts(){
        return regType.split(SEPARATOR);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BonjourService service = (BonjourService) o;

        if (flags != service.flags) return false;
        if (ifIndex != service.ifIndex) return false;
        if (!serviceName.equals(service.serviceName)) return false;
        if (!regType.equals(service.regType)) return false;
        return domain.equals(service.domain);

    }

    @Override
    public int hashCode() {
        int result = flags;
        result = 31 * result + serviceName.hashCode();
        result = 31 * result + regType.hashCode();
        result = 31 * result + domain.hashCode();
        result = 31 * result + ifIndex;
        return result;
    }
}

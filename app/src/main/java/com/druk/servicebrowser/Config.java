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
package com.druk.servicebrowser;

import java.util.regex.Pattern;

public final class Config {

    private static final boolean VERBOSE = false;

    /*
     *   @see {http://files.dns-sd.org/draft-cheshire-dnsext-dns-sd.txt}
     */
    public static final String SERVICES_DOMAIN = "_services._dns-sd._udp";

    public static final String EMPTY_DOMAIN = ".";
    public static final String LOCAL_DOMAIN = "local.";
    public static final String TCP_REG_TYPE_SUFFIX = "_tcp";
    public static final String UDP_REG_TYPE_SUFFIX = "_udp";

    public static final String REG_TYPE_SEPARATOR = Pattern.quote(".");
}

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
package com.druk.bonjourbrowser.dnssd;

import android.content.Context;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.util.Log;

import com.apple.dnssd.BrowseListener;
import com.apple.dnssd.DNSSD;
import com.apple.dnssd.DNSSDException;
import com.apple.dnssd.DNSSDService;
import com.apple.dnssd.QueryListener;
import com.apple.dnssd.ResolveListener;
import com.apple.dnssd.TXTRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.util.Map;
import java.util.TreeMap;

import rx.Observable;
import rx.Subscriber;

public class RxDNSSD {

    private static final String TAG = "RxDNSSD";
    private static final TreeMap<String, String> SERVICE_NAMES_TREE = new TreeMap<>();

    public static void init(Context ctx){
        ctx.getSystemService(Context.NSD_SERVICE);

        try {
            InputStream is = ctx.getAssets().open("service-names-port-numbers.csv");
            try {

                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] rowData = line.split(",");
                    if (rowData.length < 4 || TextUtils.isEmpty(rowData[0]) || TextUtils.isEmpty(rowData[2]) || TextUtils.isEmpty(rowData[3])){
                        continue;
                    }
                    SERVICE_NAMES_TREE.put("_" + rowData[0] + "._" + rowData[2] + ".", rowData[3]);
                    // do something with "data" and "value"
                }
            }
            catch (IOException ex) {
                // handle exception
            }
            finally {
                try {
                    is.close();
                }
                catch (IOException e) {
                    // handle exception
                }

//                for (String key : SERVICE_NAMES_TREE.keySet()){
//                    Log.d("TREE", key + " = " + SERVICE_NAMES_TREE.get(key));
//                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Observable<BonjourService> resolve(final Observable<BonjourService> observable){
        return observable.flatMap(bs -> {
            if (bs.isDeleted){
                return Observable.just(bs);
            }
            return Observable.create(new Observable.OnSubscribe<BonjourService>() {
                @Override
                public void call(final Subscriber<? super BonjourService> subscriber) {
                    try {
                        DNSSD.resolve(bs.flags, bs.ifIndex, bs.serviceName, bs.regType, bs.domain, new ResolveListener() {
                            @Override
                            public void serviceResolved(DNSSDService resolver, int flags, int ifIndex, String fullName, String hostName, int port, TXTRecord txtRecord) {
                                bs.port = port;
                                bs.hostname = hostName;
                                bs.dnsRecords.clear();
                                bs.dnsRecords.put(BonjourService.DNS_RECORD_KEY_ADDRESS, "0.0.0.0:" + bs.port);
                                bs.dnsRecords.putAll(parseTXTRecords(txtRecord));
                                bs.timestamp = System.currentTimeMillis();
                                subscriber.onNext(bs);
                                resolver.stop();
                                subscriber.onCompleted();
                            }

                            @Override
                            public void operationFailed(DNSSDService service, int errorCode) {
                                subscriber.onError(new RuntimeException("DNSSD resolve error: " + errorCode));
                            }
                        });
                    } catch (DNSSDException e) {
                        Log.e(TAG, e.getMessage() + " for " + bs.regType + " " + bs.serviceName + " " + bs.domain);
                        e.printStackTrace();
                        subscriber.onError(e);
                    }
                }
            });
        });
    }

    public static Observable<BonjourService> queryRecords(final Observable<BonjourService> observable){
        return observable.flatMap(bs -> {
            if (bs.isDeleted){
                return Observable.just(bs);
            }
            return Observable.create(new Observable.OnSubscribe<BonjourService>() {
                @Override
                public void call(final Subscriber<? super BonjourService> subscriber) {
                    try {
                        // Start a record query to obtain IP address from hostname
                        DNSSD.queryRecord(0, bs.ifIndex, bs.hostname, 1 /* ns_t_a */, 1 /* ns_c_in */,
                                new QueryListener() {
                                    @Override
                                    public void queryAnswered(DNSSDService query, int flags, int ifIndex, String fullName, int rrtype, int rrclass, byte[] rdata, int ttl) {
                                        try {
                                            InetAddress address = InetAddress.getByAddress(rdata);
                                            bs.dnsRecords.put(BonjourService.DNS_RECORD_KEY_ADDRESS, address.getHostAddress() + ":" + bs.port);
                                            subscriber.onNext(bs);
                                            subscriber.onCompleted();
                                        } catch (Exception e) {
                                            subscriber.onError(e);
                                        } finally {
                                            query.stop();
                                        }
                                    }

                                    @Override
                                    public void operationFailed(DNSSDService service, int errorCode) {
                                        subscriber.onError(new RuntimeException("DNSSD queryRecord error: " + errorCode));
                                    }
                                });
                    } catch (Exception e) {
                        subscriber.onError(e);
                    }
                }
            });
        });
    }

    public static Observable<BonjourService> browse(final String regType, final String domain) {
        final DNSSDService[] service = new DNSSDService[1];
        return Observable.create(new Observable.OnSubscribe<BonjourService>() {
            @Override
            public void call(final Subscriber<? super BonjourService> subscriber) {
                try {
                    service[0] = DNSSD.browse(0, 0, regType, domain, new BrowseListener() {
                        @Override
                        public void serviceFound(DNSSDService browser, int flags, int ifIndex, String serviceName, String regType, String domain) {
                            BonjourService service = new BonjourService(flags, ifIndex, serviceName, regType, domain);
                            subscriber.onNext(service);
                        }

                        @Override
                        public void serviceLost(DNSSDService browser, int flags, int ifIndex, String serviceName, String regType, String domain) {
                            BonjourService service = new BonjourService(flags, ifIndex, serviceName, regType, domain);
                            service.isDeleted = true;
                            subscriber.onNext(service);
                        }

                        @Override
                        public void operationFailed(DNSSDService service, int errorCode) {
                            subscriber.onError(new RuntimeException("DNSSD browse error: " + errorCode));
                        }
                    });
                } catch (DNSSDException e) {
                    e.printStackTrace();
                    subscriber.onError(e);
                }
            }
        }).doOnUnsubscribe(() -> {
            if (service[0] != null){
                service[0].stop();
                service[0] = null;
            }
        });
    }

    public static String getRegTypeDescription(String regType){
        return SERVICE_NAMES_TREE.get(regType);
    }

    private static Map<String, String> parseTXTRecords(TXTRecord record){
        Map<String, String> result = new ArrayMap<>();
        for (int i = 0; i < record.size(); i++){
            if (!TextUtils.isEmpty(record.getKey(i)) && !TextUtils.isEmpty(record.getValueAsString(i)))
                result.put(record.getKey(i), record.getValueAsString(i));
        }
        return result;
    }

}

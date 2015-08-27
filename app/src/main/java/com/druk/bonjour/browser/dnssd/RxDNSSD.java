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

import com.apple.dnssd.BrowseListener;
import com.apple.dnssd.DNSSD;
import com.apple.dnssd.DNSSDException;
import com.apple.dnssd.DNSSDService;
import com.apple.dnssd.QueryListener;
import com.apple.dnssd.ResolveListener;
import com.apple.dnssd.TXTRecord;

import android.content.Context;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;

import java.net.InetAddress;
import java.util.Map;

import rx.Observable;
import rx.Subscriber;

public final class RxDNSSD {

    private static final RxDNSSD INSTANCE = new RxDNSSD();
    private Context mContext;

    public static void init(Context ctx) {
        INSTANCE.mContext = ctx;
    }

    public static Observable<BonjourService> resolve(Observable<BonjourService> observable) {
        return observable.flatMap(bs -> {
            if (bs.isDeleted) {
                return Observable.just(bs);
            }
            return new RxDNSSDService(){

                @Override
                protected DNSSDService getService(Subscriber<? super BonjourService> subscriber) throws DNSSDException {

                    return DNSSD.resolve(bs.flags, bs.ifIndex, bs.serviceName, bs.regType, bs.domain, new ResolveListener() {
                        @Override
                        public void serviceResolved(DNSSDService resolver, int flags, int ifIndex, String fullName, String hostName, int port, TXTRecord txtRecord) {
                            bs.port = port;
                            bs.hostname = hostName;
                            bs.dnsRecords.clear();
                            bs.dnsRecords.put(BonjourService.DNS_RECORD_KEY_ADDRESS, "0.0.0.0:" + bs.port);
                            bs.dnsRecords.putAll(parseTXTRecords(txtRecord));
                            bs.timestamp = System.currentTimeMillis();
                            subscriber.onNext(bs);
                            subscriber.onCompleted();
                            resolver.stop();
                        }

                        @Override
                        public void operationFailed(DNSSDService service, int errorCode) {
                            subscriber.onError(new RuntimeException("DNSSD resolve error: " + errorCode));
                        }
                    });

                }
            }.getObservable(INSTANCE.mContext);
        });
    }

    public static Observable<BonjourService> queryRecords(Observable<BonjourService> observable) {
        return observable.flatMap(bs -> {
            if (bs.isDeleted) {
                return Observable.just(bs);
            }
            return new RxDNSSDService() {

                @Override
                protected DNSSDService getService(Subscriber<? super BonjourService> subscriber) throws DNSSDException {
                    return DNSSD.queryRecord(0, bs.ifIndex, bs.hostname, 1 /* ns_t_a */, 1 /* ns_c_in */, new QueryListener() {

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
                }

            }.getObservable(INSTANCE.mContext);
        });
    }

    public static Observable<BonjourService> browse(final String regType, final String domain) {
        return new RxDNSSDService(){

            @Override
            protected DNSSDService getService(Subscriber<? super BonjourService> subscriber) throws DNSSDException {
                return DNSSD.browse(0, 0, regType, domain, new BrowseListener() {
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
            }
        }.getObservable(INSTANCE.mContext);
    }

    private static Map<String, String> parseTXTRecords(TXTRecord record) {
        Map<String, String> result = new ArrayMap<>();
        for (int i = 0; i < record.size(); i++) {
            if (!TextUtils.isEmpty(record.getKey(i)) && !TextUtils.isEmpty(record.getValueAsString(i)))
                result.put(record.getKey(i), record.getValueAsString(i));
        }
        return result;
    }

    private abstract static class RxDNSSDService {

        private DNSSDService mService;

        private RxDNSSDService(){}

        private void startDaemonProcess(Context ctx) {
            ctx.getSystemService(Context.NSD_SERVICE);
        }

        protected abstract DNSSDService getService(Subscriber<? super BonjourService> subscriber) throws DNSSDException;

        protected Observable<BonjourService> getObservable(final Context context){
            return Observable.create(new Observable.OnSubscribe<BonjourService>() {
                @Override
                public void call(Subscriber<? super BonjourService> subscriber) {
                    try {
                        startDaemonProcess(context);
                        mService = getService(subscriber);
                    } catch (DNSSDException e) {
                        e.printStackTrace();
                        subscriber.onError(e);
                    }
                }
            }).doOnUnsubscribe(() -> {
                if (mService != null){
                    mService.stop();
                    mService = null;
                }
            });
        }
    }

}

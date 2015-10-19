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

import com.apple.dnssd.DNSSD;
import com.apple.dnssd.DNSSDException;
import com.apple.dnssd.DNSSDService;
import com.apple.dnssd.DomainListener;
import com.apple.dnssd.TXTRecord;

import android.content.Context;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;

import java.net.InetAddress;
import java.util.Map;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;

public final class RxDNSSD {

    private static final RxDNSSD INSTANCE = new RxDNSSD();
    private Context mContext;

    public static void init(Context ctx) {
        INSTANCE.mContext = ctx.getApplicationContext();
    }

    public interface DNSSDServiceCreator<T>{
        DNSSDService getService(Subscriber<? super T> subscriber) throws DNSSDException;
    }

    private <T> Observable<T> createObservable(DNSSDServiceCreator<T> mCreator){
        final DNSSDService[] mService = new DNSSDService[1];
        return Observable.create(new OnSubscribe<T>() {
            @Override
            public void call(Subscriber<? super T> subscriber) {
                if (!subscriber.isUnsubscribed()) {
                    mContext.getSystemService(Context.NSD_SERVICE);
                    try {
                        mService[0] = mCreator.getService(subscriber);
                    } catch (DNSSDException e) {
                        e.printStackTrace();
                        subscriber.onError(e);
                    }
                }
            }
        }).doOnUnsubscribe(() -> {
            if (mService[0] != null) {
                Observable.just(mService[0])
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(DNSSDService::stop);
                mService[0] = null;
            }
        });
    }

    public static Observable<BonjourService> browse(final String regType, final String domain) {
        return INSTANCE.createObservable(subscriber -> DNSSD.browse(0, DNSSD.ALL_INTERFACES, regType, domain,
                new BrowseListener(subscriber)));
    }

    public static Observable.Transformer<BonjourService, BonjourService> resolve() {
        return observable -> observable.flatMap(bs -> {
            if ((bs.flags & BonjourService.DELETED) == BonjourService.DELETED) {
                return Observable.just(bs);
            }
            return INSTANCE.createObservable(subscriber -> DNSSD.resolve(bs.flags, bs.ifIndex, bs.serviceName, bs.regType, bs.domain,
                    new ResolveListener(subscriber, bs)));
        });
    }

    public static Observable.Transformer<BonjourService, BonjourService> queryRecords() {
        return observable -> observable.flatMap(bs -> {
            if ((bs.flags & BonjourService.DELETED) == BonjourService.DELETED) {
                return Observable.just(bs);
            }
            return INSTANCE.createObservable(subscriber -> DNSSD.queryRecord(0, bs.ifIndex, bs.hostname, 1 /* ns_t_a */, 1 /* ns_c_in */,
                    new QueryListener(subscriber, bs)));
        });
    }

    public static Observable<String> enumerateDomains() {
        return INSTANCE.createObservable(subscriber -> DNSSD.enumerateDomains(DNSSD.BROWSE_DOMAINS, DNSSD.ALL_INTERFACES, new DomainListener() {

            @Override
            public void domainFound(DNSSDService domainEnum, int flags, int ifIndex, String domain) {
                if (subscriber.isUnsubscribed()) {
                    return;
                }
                subscriber.onNext(domain);
            }

            @Override
            public void domainLost(DNSSDService domainEnum, int flags, int ifIndex, String domain) {

            }

            @Override
            public void operationFailed(DNSSDService service, int errorCode) {
                if (subscriber.isUnsubscribed()) {
                    return;
                }
                subscriber.onError(new RuntimeException("DNSSD browse error: " + errorCode));
            }
        }));
    }

    private static class BrowseListener implements com.apple.dnssd.BrowseListener{
        private Subscriber<? super BonjourService> mSubscriber;

        private BrowseListener(Subscriber<? super BonjourService> subscriber){
            mSubscriber = subscriber;
        }

        @Override
        public void serviceFound(DNSSDService browser, int flags, int ifIndex, String serviceName, String regType, String domain) {
            if (mSubscriber.isUnsubscribed()){
                return;
            }
            BonjourService service = new BonjourService(flags, ifIndex, serviceName, regType, domain);
            mSubscriber.onNext(service);
        }

        @Override
        public void serviceLost(DNSSDService browser, int flags, int ifIndex, String serviceName, String regType, String domain) {
            if (mSubscriber.isUnsubscribed()){
                return;
            }
            BonjourService service = new BonjourService(flags | BonjourService.DELETED, ifIndex, serviceName, regType, domain);
            mSubscriber.onNext(service);
        }

        @Override
        public void operationFailed(DNSSDService service, int errorCode) {
            if (mSubscriber.isUnsubscribed()){
                return;
            }
            mSubscriber.onError(new RuntimeException("DNSSD browse error: " + errorCode));
        }
    }

    private static class ResolveListener implements com.apple.dnssd.ResolveListener{
        private Subscriber<? super BonjourService> mSubscriber;
        private BonjourService mBonjourService;

        private ResolveListener(Subscriber<? super BonjourService> subscriber, BonjourService service){
            mSubscriber = subscriber;
            mBonjourService = service;
        }

        @Override
        public void serviceResolved(DNSSDService resolver, int flags, int ifIndex, String fullName, String hostName, int port, TXTRecord txtRecord) {
            if (mSubscriber.isUnsubscribed()){
                return;
            }
            mBonjourService.port = port;
            mBonjourService.hostname = hostName;
            mBonjourService.dnsRecords.clear();
            mBonjourService.dnsRecords.put(BonjourService.DNS_RECORD_KEY_ADDRESS, "0.0.0.0:" + mBonjourService.port);
            mBonjourService.dnsRecords.putAll(parseTXTRecords(txtRecord));
            mBonjourService.timestamp = System.currentTimeMillis();
            mSubscriber.onNext(mBonjourService);
            mSubscriber.onCompleted();
        }

        @Override
        public void operationFailed(DNSSDService service, int errorCode) {
            if (mSubscriber.isUnsubscribed()){
                return;
            }
            mSubscriber.onError(new RuntimeException("DNSSD resolve error: " + errorCode));
        }
    }

    private static class QueryListener implements com.apple.dnssd.QueryListener{

        private final Subscriber<? super BonjourService> mSubscriber;
        private final BonjourService mBonjourService;

        private QueryListener(Subscriber<? super BonjourService> subscriber, BonjourService bonjourService){
            mSubscriber = subscriber;
            mBonjourService = bonjourService;
        }

        @Override
        public void queryAnswered(DNSSDService query, int flags, int ifIndex, String fullName, int rrtype, int rrclass, byte[] rdata, int ttl) {
            if (mSubscriber.isUnsubscribed()){
                return;
            }
            try {
                InetAddress address = InetAddress.getByAddress(rdata);
                mBonjourService.dnsRecords.put(BonjourService.DNS_RECORD_KEY_ADDRESS, address.getHostAddress() + ":" + mBonjourService.port);
                mSubscriber.onNext(mBonjourService);
                mSubscriber.onCompleted();
            } catch (Exception e) {
                mSubscriber.onError(e);
            }
        }

        @Override
        public void operationFailed(DNSSDService service, int errorCode) {
            if (mSubscriber.isUnsubscribed()){
                return;
            }
            mSubscriber.onError(new RuntimeException("DNSSD queryRecord error: " + errorCode));
        }
    }

    private static Map<String, String> parseTXTRecords(TXTRecord record) {
        Map<String, String> result = new ArrayMap<>();
        for (int i = 0; i < record.size(); i++) {
            if (!TextUtils.isEmpty(record.getKey(i)) && !TextUtils.isEmpty(record.getValueAsString(i)))
                result.put(record.getKey(i), record.getValueAsString(i));
        }
        return result;
    }
}

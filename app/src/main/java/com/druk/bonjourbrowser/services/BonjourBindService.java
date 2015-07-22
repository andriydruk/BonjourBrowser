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
package com.druk.bonjourbrowser.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
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
import com.druk.bonjourbrowser.Config;
import com.druk.bonjourbrowser.entity.BonjourService;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import rx.Observable;
import rx.Subscriber;

import static com.druk.bonjourbrowser.Config.EMPTY_DOMAIN;
import static com.druk.bonjourbrowser.Config.TCP_REG_TYPE_SUFFIX;
import static com.druk.bonjourbrowser.Config.UDP_REG_TYPE_SUFFIX;

public class BonjourBindService extends Service implements BrowseListener {

    private static final String TAG = "BBS";

    // Binder given to clients
    private final IBinder mBinder = new LocalBinder();
    private final TreeMap<String, BonjourService> mServices = new TreeMap<>();
    private final Map<String, DNSSDService> mBrowsers = new ArrayMap<>();
    private final Map<String, Set<Subscriber<? super Collection<BonjourService>>>> mListeners = new ArrayMap<>();
    private DNSSDService mDomainBrowser;

    public static String createKey(String domain, String regType){
        return createKey(domain, regType, "");
    }

    public static String createKey(String domain, String regType, String serviceName){
        return domain + regType + serviceName;
    }

    private static Map<String, String> parseTXTRecords(TXTRecord record){
        Map<String, String> result = new ArrayMap<>();
        for (int i = 0; i < record.size(); i++){
            if (!TextUtils.isEmpty(record.getKey(i)) && !TextUtils.isEmpty(record.getValueAsString(i)))
                result.put(record.getKey(i), record.getValueAsString(i));
        }
        return result;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        getSystemService(Context.NSD_SERVICE);

        try {
            mDomainBrowser = DNSSD.browse(Config.SERVICES_DOMAIN, this);
        } catch (DNSSDException e) {
            Log.w(TAG, "Starting browser error ", e);
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        mDomainBrowser.stop();
        mDomainBrowser = null;
        for (DNSSDService service : mBrowsers.values()){
            service.stop();
        }
        mServices.clear();
        mBrowsers.clear();
        mListeners.clear();
    }

    public Observable<Collection<BonjourService>> listenChanges(final String domain, final String regType){
        return Observable.create(new Observable.OnSubscribe<Collection<BonjourService>>() {
            @Override
            public void call(Subscriber<? super Collection<BonjourService>> subscriber) {
                String key = createKey(domain, regType);
                Set<Subscriber<? super Collection<BonjourService>>> onBrowserListeners = mListeners.get(regType);
                if (onBrowserListeners == null){
                    onBrowserListeners = new HashSet<>();
                    mListeners.put(key, onBrowserListeners);
                }
                onBrowserListeners.add(subscriber);
                synchronized (mBinder) {
                    subscriber.onNext(new ArrayList<>(mServices.subMap(key, key + Character.MAX_VALUE).values()));
                }
            }
        });
    }

    public Observable<BonjourService> resolve(final String key){
        return Observable.create(new Observable.OnSubscribe<BonjourService>() {
            @Override
            public void call(final Subscriber<? super BonjourService> subscriber) {
                BonjourService bs = mServices.get(key);
                try {
                    DNSSD.resolve(bs.flags, bs.ifIndex, bs.serviceName, bs.regType, bs.domain, new ResolveListener() {
                        @Override
                        public void serviceResolved(DNSSDService resolver, int flags, int ifIndex, String fullName, String hostName, int port, TXTRecord txtRecord) {
                            final BonjourService bs = mServices.get(key);
                            if (bs != null) {
                                bs.fullServiceName = fullName;
                                bs.port = port;
                                bs.hostname = hostName;
                                bs.dnsRecords.clear();
                                bs.dnsRecords.put(BonjourService.DNS_RECORD_KEY_ADDRESS, "0.0.0.0:" + bs.port);
                                bs.dnsRecords.putAll(parseTXTRecords(txtRecord));
                                bs.timestamp = System.currentTimeMillis();
                                try {
                                    // Start a record query to obtain IP address from hostname
                                    DNSSD.queryRecord(0, ifIndex, hostName, 1 /* ns_t_a */, 1 /* ns_c_in */,
                                            new QueryListener() {
                                                @Override
                                                public void queryAnswered(DNSSDService query, int flags, int ifIndex, String fullName, int rrtype, int rrclass, byte[] rdata, int ttl) {
                                                    try {
                                                        InetAddress address = InetAddress.getByAddress(rdata);
                                                        bs.dnsRecords.put(BonjourService.DNS_RECORD_KEY_ADDRESS, address.getHostAddress() + ":" + bs.port);
                                                        subscriber.onNext(bs);
                                                        subscriber.onCompleted();
                                                        notifyListeners(createKey(bs.domain, bs.regType));
                                                    }
                                                    catch (Exception e) {
                                                        subscriber.onNext(bs);
                                                        subscriber.onError(e);
                                                    }
                                                    finally {
                                                        query.stop();
                                                    }
                                                }

                                                @Override
                                                public void operationFailed(DNSSDService service, int errorCode) {
                                                    subscriber.onNext(bs);
                                                    subscriber.onError(new RuntimeException("DNSSD queryRecord error: " + errorCode));
                                                }
                                            });
                                }
                                catch ( Exception e) {
                                    subscriber.onNext(bs);
                                    subscriber.onError(e);
                                }
                            }
                            resolver.stop();
                        }

                        @Override
                        public void operationFailed(DNSSDService service, int errorCode){
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
    }

    private void notifyListeners(final String key){
        Collection<BonjourService> services;
        synchronized (mBinder) {
            services = new ArrayList<>(mServices.subMap(key, key + Character.MAX_VALUE).values());
        }
        if (mListeners.containsKey(key)) {
            Iterator<Subscriber<? super Collection<BonjourService>>> iterator = mListeners.get(key).iterator();
            while (iterator.hasNext()){
                Subscriber<? super Collection<BonjourService>> subscriber = iterator.next();
                if (subscriber.isUnsubscribed()){
                    iterator.remove();
                }
                else{
                    subscriber.onNext(services);
                }
            }
        }
    }

    @Override
    public void serviceFound(DNSSDService browser, int flags, int ifIndex, String serviceName, String regType, String domain) {
        BonjourService service = new BonjourService(flags, ifIndex, serviceName, regType, domain);
        String[] regTypeParts = service.getRegTypeParts();
        if (regTypeParts.length != 2) {
            Log.e(TAG, "Incorrect reg type: " + regType);
            return;
        }

        //if it's domain browser run new service browser
        if (mDomainBrowser == browser){
            String protocolSuffix = regTypeParts[0];
            String serviceDomain = regTypeParts[1];
            if (TCP_REG_TYPE_SUFFIX.equals(protocolSuffix) || UDP_REG_TYPE_SUFFIX.equals(protocolSuffix)) {
                String key = serviceName + "." + protocolSuffix;
                if (!mBrowsers.containsKey(key)) {
                    try {
                        DNSSDService serviceBrowser = DNSSD.browse(0, 0, key, serviceDomain, this);
                        mBrowsers.put(key, serviceBrowser);
                    } catch (DNSSDException e) {
                        Log.e(TAG, "Start browsing exception ", e);
                        e.printStackTrace();
                    }
                }
            }
            else {
                Log.e(TAG, "Unknown protocol suffix: " + protocolSuffix);
            }
        }
        else {
            //if it's serviceBrowser increase count of services for specific reqType
            String serviceRegType = regTypeParts[0];
            String protocolSuffix = regTypeParts[1];
            String key = createKey(EMPTY_DOMAIN, protocolSuffix + "." + domain , serviceRegType);
            BonjourService domainService = mServices.get(key);
            if (domainService != null) {
                Integer serviceCount = (domainService.dnsRecords.containsKey(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)) ?
                        Integer.parseInt(domainService.dnsRecords.get(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)) + 1 : 1;
                domainService.dnsRecords.put(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT, serviceCount.toString());
                notifyListeners(createKey(Config.EMPTY_DOMAIN, protocolSuffix));
            }
            else{
                Log.w(TAG, "Service from unknown service type " + key);
            }
        }

        synchronized (mBinder) {
            mServices.put(createKey(domain, regType, serviceName), service);
        }
        if (mDomainBrowser != browser) {
            notifyListeners(createKey(domain, regType));
            resolve(createKey(domain, regType, serviceName)).subscribe();
        }
    }

    @Override
    public void serviceLost(DNSSDService browser, int flags, int ifIndex, String serviceName, String regType, String domain) {
        BonjourService service;
        synchronized (mBinder) {
            service = mServices.remove(createKey(domain, regType, serviceName));
        }
        if (service != null && mDomainBrowser != browser){
            String[] regTypeParts = service.getRegTypeParts();
            if (regTypeParts.length != 2) {
                Log.e(TAG, "Incorrect req type: " + regType);
            }
            else {
                //it's serviceBrowser, decrease count of services for specific reqType
                String serviceRegType = regTypeParts[0];
                String protocolSuffix = regTypeParts[1];
                String key = createKey(EMPTY_DOMAIN, protocolSuffix + "." + domain, serviceRegType);
                BonjourService domainService = mServices.get(key);
                if (domainService != null) {
                    if (domainService.dnsRecords.containsKey(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)){
                        Integer count = Integer.parseInt(domainService.dnsRecords.get(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)) - 1;
                        domainService.dnsRecords.put(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT, count.toString());
                    }
                    notifyListeners(createKey(Config.EMPTY_DOMAIN, protocolSuffix));
                } else {
                    Log.w(TAG, "Service from unknown service type " + key);
                }
            }
        }
        notifyListeners(createKey(domain, regType));
    }

    @Override
    public void operationFailed(DNSSDService service, int errorCode) {
        Log.e(TAG, "Search operation failed with error code: " + errorCode);
    }

    public BonjourService getServiceForKey(String key){
        return mServices.get(key);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {

        public BonjourBindService getService(){
            return BonjourBindService.this;
        }
    }
}

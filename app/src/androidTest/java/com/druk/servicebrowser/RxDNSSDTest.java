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

import com.github.druk.rxdnssd.RxDnssd;
import com.github.druk.rxdnssd.RxDnssdBindable;

import android.test.InstrumentationTestCase;
import android.util.Log;

import java.util.HashMap;

import rx.Subscription;

import static com.druk.servicebrowser.Config.TCP_REG_TYPE_SUFFIX;
import static com.druk.servicebrowser.Config.UDP_REG_TYPE_SUFFIX;

//TODO: move to android support tests
public class RxDNSSDTest extends InstrumentationTestCase {

    private static final String TAG = "RxDNSSDTest";
    private static final int TIME_LIMIT = 5000; //5 second

    private Throwable error;
    private RxDnssd mRxDnssd;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mRxDnssd = new RxDnssdBindable(getInstrumentation().getContext());
    }

    public void testRxDNSSD() throws Throwable {
        Thread mainThread = Thread.currentThread();
        HashMap<String, Subscription> subscriptions = new HashMap<>();
        Subscription mainSubscription = mRxDnssd.browse(Config.SERVICES_DOMAIN, "")
                .subscribe(service -> {
                    if (service.isLost()) {
                        return;
                    }
                    String[] regTypeParts = service.getRegType().split(Config.REG_TYPE_SEPARATOR);
                    String protocolSuffix = regTypeParts[0];
                    String serviceDomain = regTypeParts[1];
                    if (TCP_REG_TYPE_SUFFIX.equals(protocolSuffix) || UDP_REG_TYPE_SUFFIX.equals(protocolSuffix)) {
                        String key = service.getServiceName() + "." + protocolSuffix;
                        if (!subscriptions.containsKey(key)) {
                            subscriptions.put(key, mRxDnssd.browse(key, serviceDomain).compose(mRxDnssd.resolve()).compose(mRxDnssd.queryRecords())
                                    .subscribe(service2 -> {
                                        if (service2.) {
                                            Log.d(TAG, "Lost " + service2.toString());
                                        } else {
                                            Log.d(TAG, "Found " + service2.toString() + " with port " + service2.getPort());
                                        }
                                    }, throwable -> {
                                        if (error == null) {
                                            error = throwable;
                                            mainThread.interrupt();
                                        }
                                    }));
                        }

                    }
                }, throwable -> {
                    Log.e(TAG, "Error: ", throwable);
                    if (error == null) {
                        error = throwable;
                        mainThread.interrupt();
                    }
                });
        try {
            Thread.sleep(TIME_LIMIT);
        } catch (InterruptedException e) {
            Log.e(TAG, "Error: ", error);
            throw error;
        } finally {
            mainSubscription.unsubscribe();
            for (Subscription subscription : subscriptions.values()) {
                subscription.unsubscribe();
            }
            subscriptions.clear();
            Log.d(TAG, "Subscription cleaned");
        }

    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }
}

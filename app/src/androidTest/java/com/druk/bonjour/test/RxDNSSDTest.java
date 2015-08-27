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
package com.druk.bonjour.test;

import com.druk.bonjour.browser.Config;
import com.druk.bonjour.browser.dnssd.RxDNSSD;

import android.test.InstrumentationTestCase;
import android.util.Log;

import java.util.HashMap;

import rx.Subscription;

import static com.druk.bonjour.browser.Config.TCP_REG_TYPE_SUFFIX;
import static com.druk.bonjour.browser.Config.UDP_REG_TYPE_SUFFIX;

public class RxDNSSDTest extends InstrumentationTestCase {

    private static final String TAG = "RxDNSSDTest";
    private static final int TIME_LIMIT = 5000; //5 second

    private Throwable error;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        RxDNSSD.init(getInstrumentation().getContext());
    }

    public void testRxDNSSD() throws Throwable {
        Thread mainThread = Thread.currentThread();
        HashMap<String, Subscription> subscriptions = new HashMap<>();
        Subscription mainSubscription = RxDNSSD.browse(Config.SERVICES_DOMAIN, "")
                .subscribe(service -> {
                    if (service.isDeleted) {
                        return;
                    }
                    String[] regTypeParts = service.getRegTypeParts();
                    String protocolSuffix = regTypeParts[0];
                    String serviceDomain = regTypeParts[1];
                    if (TCP_REG_TYPE_SUFFIX.equals(protocolSuffix) || UDP_REG_TYPE_SUFFIX.equals(protocolSuffix)) {
                        String key = service.serviceName + "." + protocolSuffix;
                        if (!subscriptions.containsKey(key)) {
                            subscriptions.put(key, RxDNSSD.queryRecords(RxDNSSD.resolve(RxDNSSD.browse(key, serviceDomain)))
                                    .subscribe(service2 -> {
                                        if (service2.isDeleted) {
                                            Log.d(TAG, "Lost " + service2.toString());
                                        } else {
                                            Log.d(TAG, "Found " + service2.toString() + " with port " + service2.port);
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

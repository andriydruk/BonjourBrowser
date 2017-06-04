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
package com.druk.servicebrowser.ui.fragment;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.Config;
import com.druk.servicebrowser.RegTypeManager;
import com.druk.servicebrowser.ui.adapter.ServiceAdapter;
import com.github.druk.rxdnssd.BonjourService;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

import java.util.HashMap;

import rx.BackpressureOverflow;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.exceptions.MissingBackpressureException;
import rx.functions.Action0;
import rx.functions.Action1;
import rx.functions.Func1;
import rx.schedulers.Schedulers;

import static com.druk.servicebrowser.Config.EMPTY_DOMAIN;
import static com.druk.servicebrowser.Config.TCP_REG_TYPE_SUFFIX;
import static com.druk.servicebrowser.Config.UDP_REG_TYPE_SUFFIX;


public class RegTypeBrowserFragment extends ServiceBrowserFragment {

    private static final String TAG = "RegTypeBrowser";

    private final HashMap<String, Subscription> mBrowsers = new HashMap<>();
    private final HashMap<String, BonjourDomain> mServices = new HashMap<>();
    private RegTypeManager mRegTypeManager;

    public static Fragment newInstance(String regType) {
        return fillArguments(new RegTypeBrowserFragment(), EMPTY_DOMAIN, regType);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRegTypeManager = BonjourApplication.getRegTypeManager(getContext());
        mAdapter = new ServiceAdapter(getActivity()) {
            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int i) {
                BonjourDomain domain = (BonjourDomain) getItem(i);
                String regType = domain.getServiceName() + "." + domain.getRegType().split(Config.REG_TYPE_SEPARATOR)[0] + ".";
                String regTypeDescription = mRegTypeManager.getRegTypeDescription(regType);
                if (regTypeDescription != null) {
                    viewHolder.text1.setText(regType + " (" + regTypeDescription + ")");
                } else {
                    viewHolder.text1.setText(regType);
                }
                viewHolder.text2.setText(domain.serviceCount + " services");
                viewHolder.itemView.setOnClickListener(mListener);
                viewHolder.itemView.setBackgroundResource(getBackground(i));
            }
        };
    }

    @Override
    protected void startDiscovery() {
        mSubscription = mRxDnssd.browse(Config.SERVICES_DOMAIN, "local.")
                .subscribeOn(Schedulers.io())
                .subscribe(reqTypeAction, errorAction);
    }

    @Override
    protected void stopDiscovery() {
        super.stopDiscovery();
        mServices.clear();
        synchronized (this) {
            for (Subscription subscription : mBrowsers.values()) {
                subscription.unsubscribe();
            }
            mBrowsers.clear();
        }
    }

    private final Action1<BonjourService> reqTypeAction = new Action1<BonjourService>() {
        @Override
        public void call(BonjourService service) {
            if (service.isLost()) {
                //Ignore this call
                return;
            }
            String[] regTypeParts = service.getRegType().split(Config.REG_TYPE_SEPARATOR);
            String protocolSuffix = regTypeParts[0];
            String serviceDomain = regTypeParts[1];
            if (TCP_REG_TYPE_SUFFIX.equals(protocolSuffix) || UDP_REG_TYPE_SUFFIX.equals(protocolSuffix)) {
                String key = service.getServiceName() + "." + protocolSuffix;
                synchronized (this) {
                    if (!mBrowsers.containsKey(key)) {
                        mBrowsers.put(key, mRxDnssd.browse(key, serviceDomain)
                                .onBackpressureBuffer(1000, new Action0() {
                                    @Override
                                    public void call() {
                                        Log.e(TAG, "Back pressure buffer overflow");
                                    }
                                }, new BackpressureOverflow.Strategy() {
                                    @Override
                                    public boolean mayAttemptDrop() throws MissingBackpressureException {
                                        return true;
                                    }
                                })
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(RegTypeBrowserFragment.this.servicesAction, RegTypeBrowserFragment.this.errorAction));
                    }
                    mServices.put(createKey(service.getDomain(), service.getRegType(), service.getServiceName()), new BonjourDomain(service));
                }
            } else {
                Log.e("TAG", "Unknown service protocol " + protocolSuffix);
                //Just ignore service with different protocol suffixes
            }
        }
    };

    protected final Action1<Throwable> errorAction = new Action1<Throwable>() {
        @Override
        public void call(Throwable throwable) {
            Log.e("DNSSD", "Error: ", throwable);
            RegTypeBrowserFragment.this.showError(throwable);
        }
    };

    private final Action1<BonjourService> servicesAction = new Action1<BonjourService>() {
        @Override
        public void call(BonjourService service) {
            String[] regTypeParts = service.getRegType().split(Config.REG_TYPE_SEPARATOR);
            String serviceRegType = regTypeParts[0];
            String protocolSuffix = regTypeParts[1];
            String key = createKey(EMPTY_DOMAIN, protocolSuffix + "." + service.getDomain(), serviceRegType);
            BonjourDomain domain = mServices.get(key);
            if (domain != null) {
                if (service.isLost()) {
                    domain.serviceCount--;
                } else {
                    domain.serviceCount++;
                }
                final int itemsCount = mAdapter.getItemCount();
                mAdapter.clear();
                Observable.from(mServices.values())
                        .filter(new Func1<BonjourDomain, Boolean>() {
                            @Override
                            public Boolean call(BonjourDomain bonjourDomain) {
                                return bonjourDomain.serviceCount > 0;
                            }
                        })
                        .subscribe(new Action1<BonjourDomain>() {
                            @Override
                            public void call(BonjourDomain service1) {
                                mAdapter.add(service1);
                            }
                        }, new Action1<Throwable>() {
                            @Override
                            public void call(Throwable throwable) {/* empty */}
                        }, new Action0() {
                            @Override
                            public void call() {
                                RegTypeBrowserFragment.this.showList(itemsCount);
                                mAdapter.notifyDataSetChanged();
                            }
                        });
            } else {
                Log.w(TAG, "Service from unknown service type " + key);
            }
        }
    };

    public static String createKey(String domain, String regType, String serviceName) {
        return domain + regType + serviceName;
    }

    public static class BonjourDomain extends BonjourService {
        public int serviceCount = 0;

        public BonjourDomain(BonjourService bonjourService){
            super(new BonjourService.Builder(bonjourService));
        }
    }
}

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
package com.druk.bonjour.browser.ui.fragment;

import com.apple.dnssd.DNSSD;
import com.druk.bonjour.browser.BonjourApplication;
import com.druk.bonjour.browser.Config;
import com.druk.bonjour.browser.dnssd.BonjourService;
import com.druk.bonjour.browser.dnssd.RxDNSSD;
import com.druk.bonjour.browser.ui.RegTypeActivity;
import com.druk.bonjour.browser.ui.adapter.ServiceAdapter;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;

import java.util.HashMap;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

import static com.druk.bonjour.browser.Config.EMPTY_DOMAIN;
import static com.druk.bonjour.browser.Config.TCP_REG_TYPE_SUFFIX;
import static com.druk.bonjour.browser.Config.UDP_REG_TYPE_SUFFIX;

public class RegTypeBrowserFragment extends ServiceBrowserFragment {

    private static final String TAG = "RegTypeBrowser";

    private final HashMap<String, Subscription> mBrowsers = new HashMap<>();
    private final HashMap<String, BonjourService> mServices = new HashMap<>();

    public static Fragment newInstance(String regType) {
        return fillArguments(new RegTypeBrowserFragment(), Config.EMPTY_DOMAIN, regType);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new ServiceAdapter(getActivity()) {
            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int i) {
                BonjourService service = getItem(i);
                String regType = service.serviceName + "." + service.getRegTypeParts()[0] + ".";
                String regTypeDescription = BonjourApplication.getRegTypeDescription(viewHolder.itemView.getContext(), regType);
                if (regTypeDescription != null) {
                    viewHolder.binding.text1.setText(regType + " (" + regTypeDescription + ")");
                } else {
                    viewHolder.binding.text1.setText(regType);
                }
                viewHolder.binding.text2.setText(service.dnsRecords.get(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT) + " services");
                viewHolder.itemView.setOnClickListener(mListener);
                viewHolder.itemView.setBackgroundResource(getBackground(i));
            }
        };
    }

    @Override
    protected void startDiscovery() {
        mSubscription = RxDNSSD.browse(Config.SERVICES_DOMAIN, "local.")
                .subscribe(reqTypeAction, errorAction);
    }

    @Override
    protected void stopDiscovery() {
        super.stopDiscovery();
        mServices.clear();
        for (Subscription subscription : mBrowsers.values()) {
            subscription.unsubscribe();
        }
        mBrowsers.clear();
    }

    private final Action1<BonjourService> reqTypeAction = service -> {
        if ((service.flags & BonjourService.DELETED) == BonjourService.DELETED){
            Log.d("TAG", "Lose reg type: " + service);
            //Ignore this call
            return;
        }
        String[] regTypeParts = service.getRegTypeParts();
        String protocolSuffix = regTypeParts[0];
        String serviceDomain = regTypeParts[1];
        if (TCP_REG_TYPE_SUFFIX.equals(protocolSuffix) || UDP_REG_TYPE_SUFFIX.equals(protocolSuffix)) {
            String key = service.serviceName + "." + protocolSuffix;
            if (!mBrowsers.containsKey(key)) {
                mBrowsers.put(key, RxDNSSD.browse(key, serviceDomain)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(RegTypeBrowserFragment.this.servicesAction, RegTypeBrowserFragment.this.errorAction));
            }
            mServices.put(createKey(service.domain, service.regType, service.serviceName), service);
        } else {
            //Just ignore service with different protocol suffixes
        }
    };

    protected final Action1<Throwable> errorAction = (Throwable throwable) -> {
        Log.e("DNSSD", "Error: ", throwable);
    };

    private final Action1<BonjourService> servicesAction = service -> {
        String[] regTypeParts = service.getRegTypeParts();
        String serviceRegType = regTypeParts[0];
        String protocolSuffix = regTypeParts[1];
        String key = createKey(EMPTY_DOMAIN, protocolSuffix + "." + service.domain, serviceRegType);
        BonjourService domainService = mServices.get(key);
        if (domainService != null) {
            Integer serviceCount = (domainService.dnsRecords.containsKey(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)) ?
                    Integer.parseInt(domainService.dnsRecords.get(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)) : 0;
            if ((service.flags & BonjourService.DELETED) == BonjourService.DELETED){
                Log.d("TAG", "Lst service: " + service);
                serviceCount--;
            } else {
                serviceCount++;
            }
            domainService.dnsRecords.put(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT, serviceCount.toString());

            if ((service.flags & DNSSD.MORE_COMING) != DNSSD.MORE_COMING){
                mAdapter.clear();
                Observable.from(mServices.values())
                        .filter(bonjourService -> bonjourService.dnsRecords.containsKey(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)
                                && Integer.parseInt(bonjourService.dnsRecords.get(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)) > 0)
                        .subscribe(mAdapter::add, throwable -> {/* empty */}, mAdapter::notifyDataSetChanged);
            }
        } else {
            Log.w(TAG, "Service from unknown service type " + key);
        }
    };

    public static String createKey(String domain, String regType, String serviceName) {
        return domain + regType + serviceName;
    }
}

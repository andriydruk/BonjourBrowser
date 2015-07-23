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
package com.druk.bonjourbrowser.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;

import com.druk.bonjourbrowser.Config;
import com.druk.bonjourbrowser.dnssd.BonjourService;
import com.druk.bonjourbrowser.dnssd.RxDNSSD;
import com.druk.bonjourbrowser.ui.RegTypeActivity;
import com.druk.bonjourbrowser.ui.adapter.ServiceAdapter;

import java.util.HashMap;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

import static com.druk.bonjourbrowser.Config.EMPTY_DOMAIN;
import static com.druk.bonjourbrowser.Config.TCP_REG_TYPE_SUFFIX;
import static com.druk.bonjourbrowser.Config.UDP_REG_TYPE_SUFFIX;

public class RegTypeBrowserFragment extends ServiceBrowserFragment {

    private final HashMap<String, Subscription> mBrowsers = new HashMap<>();
    private final HashMap<String, BonjourService> mServices = new HashMap<>();

    public static Fragment newInstance(String regType){
        return fillArguments(new RegTypeBrowserFragment(), Config.EMPTY_DOMAIN, regType);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new ServiceAdapter(getActivity())
        {
            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int i) {
                final BonjourService service = getItem(i);
                viewHolder.domain.setText(service.serviceName);
                viewHolder.serviceCount.setText(service.dnsRecords.get(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT) + " services");

                viewHolder.itemView.setOnClickListener(v -> {
                    Context context = v.getContext();
                    String[] regTypeParts = service.getRegTypeParts();
                    String reqType = service.serviceName + "." +  regTypeParts[0] + ".";
                    String domain = regTypeParts[1] + ".";
                    RegTypeActivity.startActivity(context, reqType, domain);
                });
            }
        };
    }

    @Override
    protected void startDiscovery() {
        mSubscription = RxDNSSD.browse(Config.SERVICES_DOMAIN, "")
                .subscribe(reqTypeAction);
    }

    @Override
    protected void stopDiscovery() {
        super.stopDiscovery();
        mServices.clear();
        for (Subscription subscription : mBrowsers.values()){
            subscription.unsubscribe();
        }
        mBrowsers.clear();
    }

    private final Action1<BonjourService> reqTypeAction = service -> {
        if (service.isDeleted){
            //Ignore this call
            return;
        }
        String[] regTypeParts = service.getRegTypeParts();
        if (regTypeParts.length != 2) {
            //Log.e(TAG, "Incorrect reg type: " + regType);
            return;
        }
        String protocolSuffix = regTypeParts[0];
        String serviceDomain = regTypeParts[1];
        if (TCP_REG_TYPE_SUFFIX.equals(protocolSuffix) || UDP_REG_TYPE_SUFFIX.equals(protocolSuffix)) {
            String key = service.serviceName + "." + protocolSuffix;
            if (!mBrowsers.containsKey(key)) {
                mBrowsers.put(key, RxDNSSD.browse(key, serviceDomain)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(RegTypeBrowserFragment.this.servicesAction));
            }
            mServices.put(createKey(service.domain, service.regType, service.serviceName), service);
        } else {
            //Log.e(TAG, "Unknown protocol suffix: " + protocolSuffix);
        }
    };

    private final Action1<BonjourService> servicesAction = service -> {
        String[] regTypeParts = service.getRegTypeParts();
        if (regTypeParts.length != 2) {
            //Log.e(TAG, "Incorrect reg type: " + regType);
            return;
        }
        String serviceRegType = regTypeParts[0];
        String protocolSuffix = regTypeParts[1];
        String key1 = createKey(EMPTY_DOMAIN, protocolSuffix + "." + service.domain, serviceRegType);
        BonjourService domainService = mServices.get(key1);
        if (domainService != null) {
            Integer serviceCount = (domainService.dnsRecords.containsKey(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)) ?
                    Integer.parseInt(domainService.dnsRecords.get(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT)) : 0;
            if (service.isDeleted){
                serviceCount--;
            }
            else{
                serviceCount++;
            }
            domainService.dnsRecords.put(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT, serviceCount.toString());

            mAdapter.clear();
            Observable.from(mServices.values())
                    .filter(bonjourService -> bonjourService.dnsRecords.containsKey(BonjourService.DNS_RECORD_KEY_SERVICE_COUNT))
                    .subscribe(mAdapter::add, throwable -> {/* empty */}, mAdapter::notifyDataSetChanged);
        } else {
            //Log.w(TAG, "Service from unknown service type " + key);
        }
    };

    public static String createKey(String domain, String regType, String serviceName){
        return domain + regType + serviceName;
    }
}

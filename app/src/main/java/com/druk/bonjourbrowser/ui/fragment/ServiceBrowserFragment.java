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

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.druk.bonjourbrowser.R;
import com.druk.bonjourbrowser.dnssd.BonjourService;
import com.druk.bonjourbrowser.dnssd.RxDNSSD;
import com.druk.bonjourbrowser.ui.ServiceActivity;
import com.druk.bonjourbrowser.ui.adapter.ServiceAdapter;

import java.util.ArrayList;
import java.util.List;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class ServiceBrowserFragment extends Fragment {

    private static final String KEY_REG_TYPE = "reg_type";
    private static final String KEY_DOMAIN = "domain";

    protected Subscription mSubscription;
    protected ServiceAdapter mAdapter;
    protected String mReqType;
    protected String mDomain;

    public static Fragment newInstance(String domain, String regType){
        return fillArguments(new ServiceBrowserFragment(), domain, regType);
    }

    protected static Fragment fillArguments(Fragment fragment, String domain, String regType){
        Bundle bundle = new Bundle();
        bundle.putString(KEY_DOMAIN, domain);
        bundle.putString(KEY_REG_TYPE, regType);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null){
            mReqType = getArguments().getString(KEY_REG_TYPE);
            mDomain = getArguments().getString(KEY_DOMAIN);
        }
        mAdapter = new ServiceAdapter(getActivity()){
            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int i) {
                final BonjourService service = getItem(i);
                viewHolder.domain.setText(service.serviceName);
                if (service.timestamp > 0) {
                    viewHolder.serviceCount.setText(service.dnsRecords.get(BonjourService.DNS_RECORD_KEY_ADDRESS));
                }
                else{
                    viewHolder.serviceCount.setText(R.string.not_resolved_yet);
                }
                viewHolder.itemView.setOnClickListener(v -> ServiceActivity.startActivity(v.getContext(), service));
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        RecyclerView recyclerView = (RecyclerView) inflater.inflate(
                R.layout.fragment_service_browser, container, false);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(mAdapter);
        return recyclerView;
    }

    @Override
    public void onResume() {
        super.onResume();
        startDiscovery();
    }

    @Override
    public void onPause() {
        super.onPause();
        stopDiscovery();
    }

    protected void startDiscovery(){
        final List<BonjourService> bonjourServices = new ArrayList<>();
        mSubscription = RxDNSSD.queryRecords(RxDNSSD.resolve(RxDNSSD.browse(mReqType, mDomain)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {
                    if (!bonjourService.isDeleted) {
                        bonjourServices.add(bonjourService);
                    }
                    else{
                        bonjourServices.remove(bonjourService);
                    }
                    mAdapter.swap(bonjourServices);
                    mAdapter.notifyDataSetChanged();
                });
    }

    protected void stopDiscovery(){
        if (mSubscription != null){
            mSubscription.unsubscribe();
        }
    }
}

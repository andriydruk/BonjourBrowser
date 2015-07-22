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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.druk.bonjourbrowser.R;
import com.druk.bonjourbrowser.entity.BonjourService;
import com.druk.bonjourbrowser.services.BonjourBindService;
import com.druk.bonjourbrowser.ui.ServiceActivity;
import com.druk.bonjourbrowser.ui.adapter.ServiceAdapter;

import java.util.Collection;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.functions.Func1;

public class ServiceBrowserFragment extends Fragment {

    private static final String KEY_REG_TYPE = "reg_type";
    private static final String KEY_DOMAIN = "domain";

    protected Subscription mSubscription;
    protected ServiceAdapter mAdapter;
    protected String mReqType;
    protected String mDomain;

    private ServiceConnection mServiceConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mSubscription = ((BonjourBindService.LocalBinder) service).getService().listenChanges(mDomain, mReqType)
                    .observeOn(AndroidSchedulers.mainThread())
                    .map(getMapFunc())
                    .subscribe(new Action1<Collection<BonjourService>>() {
                        @Override
                        public void call(Collection<BonjourService> bonjourServices) {
                            mAdapter.swap(bonjourServices);
                            mAdapter.notifyDataSetChanged();
                        }
                    });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

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
                viewHolder.itemView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ServiceActivity.startActivity(v.getContext(), BonjourBindService.createKey(service.domain, service.regType, service.serviceName));
                    }
                });
            }
        };
        getActivity().bindService(new Intent(getActivity(), BonjourBindService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
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
    public void onDestroy() {
        super.onDestroy();
        if (mSubscription != null){
            mSubscription.unsubscribe();
        }
        getActivity().unbindService(mServiceConnection);
    }

    protected Func1<? super Collection<BonjourService>, Collection<BonjourService>> getMapFunc() {
        return new Func1<Collection<BonjourService>, Collection<BonjourService>>() {
            @Override
            public Collection<BonjourService> call(Collection<BonjourService> bonjourServices) {
                return bonjourServices;
            }
        };
    }
}

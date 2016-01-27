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

import com.druk.bonjour.browser.R;
import com.druk.bonjour.browser.ui.adapter.ServiceAdapter;
import com.github.druk.BonjourService;
import com.github.druk.RxDnssd;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class ServiceBrowserFragment<T> extends Fragment {

    private static final String KEY_REG_TYPE = "reg_type";
    private static final String KEY_DOMAIN = "domain";
    private static final String KEY_SELECTED_POSITION = "selected_position";

    protected Subscription mSubscription;
    protected ServiceAdapter mAdapter;
    protected String mReqType;
    protected String mDomain;
    protected RecyclerView mRecyclerView;

    protected View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int position = mRecyclerView.getLayoutManager().getPosition(v);
            mAdapter.setSelectedItemId(mAdapter.getItemId(position));
            mAdapter.notifyDataSetChanged();
            if (ServiceBrowserFragment.this.isAdded()) {
                BonjourService service = mAdapter.getItem(position);
                ((ServiceListener) ServiceBrowserFragment.this.getActivity()).onServiceWasSelected(mDomain, mReqType, service);
            }
        }
    };

    public static Fragment newInstance(String domain, String regType) {
        return fillArguments(new ServiceBrowserFragment(), domain, regType);
    }

    protected static Fragment fillArguments(Fragment fragment, String domain, String regType) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_DOMAIN, domain);
        bundle.putString(KEY_REG_TYPE, regType);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof ServiceListener)) {
            throw new IllegalArgumentException("Fragment context should implement ServiceListener interface");
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mReqType = getArguments().getString(KEY_REG_TYPE);
            mDomain = getArguments().getString(KEY_DOMAIN);
        }
        mAdapter = new ServiceAdapter(getActivity()) {
            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int i) {
                BonjourService service = getItem(i);
                viewHolder.binding.text1.setText(service.getServiceName());
                if (service.getInet4Address() != null) {
                    viewHolder.binding.text2.setText(service.getInet4Address().getHostAddress());
                }
                else if (service.getInet6Address() != null) {
                    viewHolder.binding.text2.setText(service.getInet6Address().getHostAddress());
                }
                else {
                    viewHolder.binding.text2.setText(R.string.not_resolved_yet);
                }
                viewHolder.itemView.setOnClickListener(mListener);
                viewHolder.itemView.setBackgroundResource(getBackground(i));
            }
        };
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRecyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_service_browser, container, false);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mRecyclerView.getContext()));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        if (savedInstanceState != null) {
            mAdapter.setSelectedItemId(savedInstanceState.getLong(KEY_SELECTED_POSITION, -1L));
        }
        return mRecyclerView;
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

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_SELECTED_POSITION, mAdapter.getSelectedItemId());
    }

    protected void startDiscovery() {
        mSubscription = RxDnssd.browse(mReqType, mDomain)
                .compose(RxDnssd.resolve())
                .compose(RxDnssd.queryRecords())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {
                    if ((bonjourService.getFlags() & BonjourService.DELETED) != BonjourService.DELETED) {
                        mAdapter.add(bonjourService);
                    } else {
                        mAdapter.remove(bonjourService);
                    }
                    mAdapter.notifyDataSetChanged();
                }, throwable -> {
                    Log.e("DNSSD", "Error: ", throwable);
                });
    }

    protected void stopDiscovery() {
        if (mSubscription != null) {
            mSubscription.unsubscribe();
        }
    }

    public interface ServiceListener {
        void onServiceWasSelected(String domain, String regType, BonjourService service);
    }
}

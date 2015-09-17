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
package com.druk.bonjour.browser.ui;

import com.druk.bonjour.browser.R;
import com.druk.bonjour.browser.databinding.ActivityServiceBinding;
import com.druk.bonjour.browser.dnssd.BonjourService;
import com.druk.bonjour.browser.dnssd.RxDNSSD;
import com.druk.bonjour.browser.ui.adapter.TxtRecordsAdapter;

import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class ServiceActivity extends AppCompatActivity implements OnClickListener {

    private static final String SERVICE = "mService";

    private TxtRecordsAdapter mAdapter;

    private BonjourService mService;
    private Subscription mResolveSubscription;

    private ActivityServiceBinding mBinding;

    public static void startActivity(Context context, BonjourService service) {
        context.startActivity(new Intent(context, ServiceActivity.class).
                putExtra(ServiceActivity.SERVICE, service));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_service);

        setSupportActionBar(mBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mService = getIntent().getParcelableExtra(SERVICE);
        mAdapter = new TxtRecordsAdapter(this, new ArrayMap<>());
        mBinding.recyclerView.setLayoutManager(new LinearLayoutManager(this));
        mBinding.recyclerView.setAdapter(mAdapter);
        mBinding.fab.setOnClickListener(this);

        updateUI(mService, false);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mResolveSubscription != null) {
            mResolveSubscription.unsubscribe();
        }
    }

    private void updateUI(BonjourService service, boolean withSnakeBar) {
        mBinding.setService(service);
        mAdapter.swap(service.dnsRecords);
        mAdapter.notifyDataSetChanged();

        if (withSnakeBar) {
            Snackbar.make(mBinding.serviceName, getString(R.string.service_was_resolved), Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onClick(View v) {
        v.animate().rotationBy(180).start();
        mResolveSubscription = RxDNSSD.queryRecords(RxDNSSD.resolve(Observable.just(mService)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {
                    if ((bonjourService.flags & BonjourService.DELETED) == BonjourService.DELETED) {
                        return;
                    }
                    updateUI(bonjourService, true);
                }, throwable -> {
                    Log.e("DNSSD", "Error: ", throwable);
                });
    }
}

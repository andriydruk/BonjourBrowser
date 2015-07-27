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
package com.druk.bonjourbrowser.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.druk.bonjourbrowser.R;
import com.druk.bonjourbrowser.dnssd.RxDNSSD;
import com.druk.bonjourbrowser.dnssd.BonjourService;
import com.druk.bonjourbrowser.ui.adapter.TxtRecordsAdapter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class ServiceActivity extends AppCompatActivity implements OnClickListener{

    private static final String SERVICE = "mService";
    private static final String TIME_FORMAT = "HH:mm:ss";

    private TextView serviceName;
    private TextView domain;
    private TextView regType;
    private TextView lastUpdate;

    private TxtRecordsAdapter mAdapter;

    private BonjourService mService;
    private Subscription mResolveSubscription;

    public static void startActivity(Context context, BonjourService service){
        context.startActivity(new Intent(context, ServiceActivity.class).
                putExtra(ServiceActivity.SERVICE, service));
    }

    private static String getTime(long timestamp){
        Calendar cal = Calendar.getInstance();
        TimeZone tz = cal.getTimeZone();

        SimpleDateFormat sdf = new SimpleDateFormat(TIME_FORMAT, Locale.getDefault());
        sdf.setTimeZone(tz);

        return sdf.format(new Date(timestamp));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }


        mService = getIntent().getParcelableExtra(SERVICE);
        mAdapter = new TxtRecordsAdapter(this, new ArrayMap<>());
        serviceName = (TextView) findViewById(R.id.service_name);
        domain = (TextView) findViewById(R.id.domain);
        regType = (TextView) findViewById(R.id.reg_type);
        lastUpdate = (TextView) findViewById(R.id.last_timestamp);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(mAdapter);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(this);

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

    private void updateUI(BonjourService service, boolean withSnakeBar){
        serviceName.setText(service.serviceName);
        domain.setText(getString(R.string.domain, service.domain));
        regType.setText(getString(R.string.reg_type, service.regType));
        lastUpdate.setText(getString(R.string.last_update, getTime(service.timestamp)));
        mAdapter.swap(service.dnsRecords);
        mAdapter.notifyDataSetChanged();

        if (withSnakeBar){
            Snackbar.make(serviceName, getString(R.string.service_was_resolved), Snackbar.LENGTH_LONG).show();
        }
    }

    @Override
    public void onClick(View v) {
        mResolveSubscription = RxDNSSD.queryRecords(RxDNSSD.resolve(Observable.just(mService)))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {
                    if (bonjourService.isDeleted){
                        return;
                    }
                    updateUI(bonjourService, true);
                });
    }
}

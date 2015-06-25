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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.druk.bonjourbrowser.R;
import com.druk.bonjourbrowser.entity.BonjourService;
import com.druk.bonjourbrowser.services.BonjourBindService;
import com.druk.bonjourbrowser.ui.adapter.TxtRecordsAdapter;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class ServiceActivity extends AppCompatActivity implements OnClickListener{

    private static final String KEY = "key";
    private static final String TIME_FORMAT = "HH:mm:ss";

    private TextView serviceName;
    private TextView domain;
    private TextView regType;
    private TextView lastUpdate;

    private TxtRecordsAdapter mAdapter;

    private String key;
    private Resolver resolver;
    private BonjourBindService mService;

    private ServiceConnection mServiceConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = (((BonjourBindService.LocalBinder) service).getService());
            updateUI(mService.getServiceForKey(key), false);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    public static void startActivity(Context context, String key){
        context.startActivity(new Intent(context, ServiceActivity.class).
                putExtra(ServiceActivity.KEY, key));
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

        key = getIntent().getStringExtra(KEY);

        mAdapter = new TxtRecordsAdapter(this, new ArrayMap<String, String>());

        serviceName = (TextView) findViewById(R.id.service_name);
        domain = (TextView) findViewById(R.id.domain);
        regType = (TextView) findViewById(R.id.reg_type);
        lastUpdate = (TextView) findViewById(R.id.last_timestamp);
        RecyclerView recyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        recyclerView.setAdapter(mAdapter);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resolver = new Resolver();
        resolver.setWeakReference(this);
        bindService(new Intent(this, BonjourBindService.class), mServiceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resolver.setWeakReference(null);
        unbindService(mServiceConnection);
        mService = null;
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
        mService.resolve(key, resolver);
    }

    private static class Resolver implements BonjourBindService.OnResolveListener{
        private final static String TAG = "Resolver";
        private ServiceActivity activity;

        private void setWeakReference(ServiceActivity activity){
            this.activity = activity;
        }

        @Override
        public void onResolved(final BonjourService service) {
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (activity != null){
                            activity.updateUI(service, true);
                        }
                        else {
                            Log.w(TAG, "Activity reference is empty in main thread");
                        }
                    }
                });
            }
            else{
                Log.w(TAG, "Activity reference is empty in DN-SSD thread");
            }
        }
    }
}

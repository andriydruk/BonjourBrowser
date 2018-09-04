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
package com.druk.servicebrowser.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.TextView;

import com.druk.servicebrowser.R;
import com.druk.servicebrowser.Utils;
import com.druk.servicebrowser.ui.fragment.ServiceDetailFragment;
import com.github.druk.rx2dnssd.BonjourService;

public class ServiceActivity extends AppCompatActivity implements ServiceDetailFragment.ServiceDetailListener {

    private static final String SERVICE = "mService";
    private static final String REGISTERED = "registered";

    private TextView mServiceName;
    private TextView mRegType;
    private TextView mDomain;
    private TextView mLastTimestamp;

    public static void startActivity(Context context, BonjourService service) {
        context.startActivity(new Intent(context, ServiceActivity.class).
                putExtra(ServiceActivity.SERVICE, service));
    }

    public static Intent startActivity(Context context, BonjourService service, boolean isRegistered) {
        return new Intent(context, ServiceActivity.class).putExtra(ServiceActivity.SERVICE, service).putExtra(REGISTERED, isRegistered);
    }

    public static BonjourService parseResult(Intent intent) {
        return intent.getParcelableExtra(SERVICE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service);
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        FloatingActionButton fab = findViewById(R.id.fab);
        mServiceName = findViewById(R.id.service_name);
        mRegType = findViewById(R.id.reg_type);
        mDomain = findViewById(R.id.domain);
        mLastTimestamp = findViewById(R.id.last_timestamp);

        ServiceDetailFragment serviceDetailFragment;
        boolean isRegistered = getIntent().getBooleanExtra(REGISTERED, false);

        if (savedInstanceState == null){
            BonjourService service = getIntent().getParcelableExtra(SERVICE);
            serviceDetailFragment = ServiceDetailFragment.newInstance(service);
            getSupportFragmentManager().beginTransaction().replace(R.id.content, serviceDetailFragment).commit();
        }
        else {
            serviceDetailFragment = (ServiceDetailFragment) getSupportFragmentManager().findFragmentById(R.id.content);
        }

        if (isRegistered && fab != null) {
            fab.setVisibility(View.VISIBLE);
            fab.setOnClickListener(serviceDetailFragment);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onServiceUpdated(BonjourService service) {
        mServiceName.setText(service.getServiceName());
        mDomain.setText(getString(R.string.domain, service.getDomain()));
        mRegType.setText(getString(R.string.reg_type, service.getRegType()));
        mLastTimestamp.setText(getString(R.string.last_update, Utils.formatTime(System.currentTimeMillis())));
    }

    @Override
    public void onServiceStopped(BonjourService service) {
        setResult(Activity.RESULT_OK, new Intent().putExtra(SERVICE, service));
        finish();
    }
}

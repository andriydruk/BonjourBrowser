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
import com.druk.bonjour.browser.ui.fragment.ServiceDetailFragment;
import com.github.druk.rxdnssd.BonjourService;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class ServiceActivity extends AppCompatActivity implements ServiceDetailFragment.ServiceDetailListener {

    private static final String SERVICE = "mService";
    private static final String REGISTERED = "registered";

    private ActivityServiceBinding mBinding;

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

        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_service);

        setSupportActionBar(mBinding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

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

        if (isRegistered) {
            mBinding.fab.setVisibility(View.VISIBLE);
            mBinding.fab.setOnClickListener(serviceDetailFragment);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onServiceUpdated(BonjourService service) {
        mBinding.setService(service);
        mBinding.setTimestamp(System.currentTimeMillis());
    }

    @Override
    public void onServiceStopped(BonjourService service) {
        setResult(Activity.RESULT_OK, new Intent().putExtra(SERVICE, service));
        finish();
    }
}

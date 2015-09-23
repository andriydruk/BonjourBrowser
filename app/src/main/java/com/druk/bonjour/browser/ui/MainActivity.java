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

import com.druk.bonjour.browser.Config;
import com.druk.bonjour.browser.R;
import com.druk.bonjour.browser.databinding.ActivityMainBinding;
import com.druk.bonjour.browser.dnssd.BonjourService;
import com.druk.bonjour.browser.ui.fragment.RegTypeBrowserFragment;
import com.druk.bonjour.browser.ui.fragment.ServiceBrowserFragment;
import com.druk.bonjour.browser.ui.fragment.ServiceDetailFragment;

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity implements ServiceBrowserFragment.ServiceListener, ServiceDetailFragment.ServiceDetailListener {

    private ActivityMainBinding mBinding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_main);
        mBinding.setDomain("local.");

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().
                    replace(R.id.first_panel, RegTypeBrowserFragment.newInstance(Config.TCP_REG_TYPE_SUFFIX)).commit();
        }
        else{
            mBinding.setFabOnClickListener((ServiceDetailFragment) getSupportFragmentManager().findFragmentById(R.id.third_panel));
            mBinding.setDomain(savedInstanceState.getString("domain"));
            mBinding.setRegType(savedInstanceState.getString("reg_type"));
            mBinding.setServiceName(savedInstanceState.getString("service_name"));
        }

        if (mBinding.slidingPanelLayout != null){
            mBinding.slidingPanelLayout.openPane();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bonjour_browser, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_license) {
            startActivity(new Intent(this, LicensesActivity.class));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceWasSelected(String domain, String regType, BonjourService service) {
        if (domain.equals(Config.EMPTY_DOMAIN)) {
            String[] regTypeParts = service.getRegTypeParts();
            String serviceRegType = service.serviceName + "." + regTypeParts[0] + ".";
            String serviceDomain = regTypeParts[1] + ".";

            if (mBinding.slidingPanelLayout != null) {
                getSupportFragmentManager().beginTransaction().
                        replace(R.id.second_panel, ServiceBrowserFragment.newInstance(serviceDomain, serviceRegType)).commit();
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.third_panel);
                if (fragment != null) {
                    getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                }
                mBinding.setFabOnClickListener(null);
                mBinding.setRegType(serviceRegType);
                mBinding.setServiceName(null);
            }
            else{
                RegTypeActivity.startActivity(this, serviceRegType, serviceDomain);
            }
        }
        else{
            ServiceDetailFragment fragment = ServiceDetailFragment.newInstance(service);
            getSupportFragmentManager().beginTransaction().
                    replace(R.id.third_panel, fragment).commit();
            mBinding.slidingPanelLayout.closePane();
            mBinding.setFabOnClickListener(fragment);
            mBinding.setServiceName(service.serviceName);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // FIXME: 9/22/15
        //outState.putString("domain", mBinding.getDomain());
        //outState.putString("reg_type", mBinding.getRegType());
        //outState.putString("service_name", mBinding.getServiceName());

        //~~~~  Temporary solution ~~~~
        try {
            Method getDomainMethod = mBinding.getClass().getMethod("getDomain");
            Method getRegTypeMethod = mBinding.getClass().getMethod("getRegType");
            Method getServiceNameMethod = mBinding.getClass().getMethod("getServiceName");
            outState.putString("domain", (String) getDomainMethod.invoke(mBinding));
            outState.putString("reg_type", (String) getRegTypeMethod.invoke(mBinding));
            outState.putString("service_name",(String) getServiceNameMethod.invoke(mBinding));
        } catch (SecurityException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            e.printStackTrace();
        }
        //~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onServiceUpdated(BonjourService service) {
        mBinding.setLastUpdate(service.timestamp);
    }
}

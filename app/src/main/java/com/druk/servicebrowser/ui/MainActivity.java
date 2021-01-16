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

import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.slidingpanelayout.widget.SlidingPaneLayout;
import androidx.appcompat.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.druk.servicebrowser.Config;
import com.druk.servicebrowser.R;
import com.druk.servicebrowser.ui.fragment.RegTypeBrowserFragment;
import com.druk.servicebrowser.ui.fragment.ServiceBrowserFragment;
import com.druk.servicebrowser.ui.fragment.ServiceDetailFragment;
import com.github.druk.rx2dnssd.BonjourService;

import java.net.URL;

public class MainActivity extends AppCompatActivity implements ServiceBrowserFragment.ServiceListener, ServiceDetailFragment.ServiceDetailListener {

    private static final String PARAM_DOMAIN = "param_domain";
    private static final String PARAM_REG_TYPE = "param_reg_type";
    private static final String PARAM_SERVICE_NAME = "param_service_name";

    private SlidingPaneLayout slidingPanelLayout;
    private TextView noServiceTextView;
    private TextView serviceNameTextView;
    private TextView lastUpdatedTextView;

    private String domain;
    private String regType;
    private String serviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));
        slidingPanelLayout = findViewById(R.id.sliding_panel_layout);

        if (slidingPanelLayout != null) {
            slidingPanelLayout.openPane();
            noServiceTextView = findViewById(R.id.no_service);
            serviceNameTextView = findViewById(R.id.service_name);
            lastUpdatedTextView = findViewById(R.id.last_updated);
        }

        if (savedInstanceState == null) {
            domain = Config.LOCAL_DOMAIN;
            getSupportFragmentManager().beginTransaction().
                    replace(R.id.first_panel, RegTypeBrowserFragment.newInstance(Config.TCP_REG_TYPE_SUFFIX)).commit();
        }
        else{
            domain = savedInstanceState.getString(PARAM_DOMAIN);
            regType = savedInstanceState.getString(PARAM_REG_TYPE);
            serviceName = savedInstanceState.getString(PARAM_SERVICE_NAME);
        }

        updateNavigation();
    }

    private void updateNavigation(){
        setTitle(domain + ((regType != null) ? "   >   " + regType + ((serviceName != null) ? "   >   " + serviceName : "") : ""));
        if (slidingPanelLayout != null){
            noServiceTextView.setVisibility(serviceName == null ? View.VISIBLE : View.GONE);
            serviceNameTextView.setVisibility(serviceName == null ? View.GONE : View.VISIBLE);
            serviceNameTextView.setText(serviceName);
            lastUpdatedTextView.setVisibility(serviceName == null ? View.GONE : View.VISIBLE);
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
        else if (id == R.id.action_register) {
            RegistrationsActivity.startActivity(this);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceWasSelected(String domain, String regType, BonjourService service) {
        if (domain.equals(Config.EMPTY_DOMAIN)) {
            String[] regTypeParts = service.getRegType().split(Config.REG_TYPE_SEPARATOR);
            String serviceRegType = service.getServiceName() + "." + regTypeParts[0] + ".";
            String serviceDomain = regTypeParts[1] + ".";

            if (slidingPanelLayout != null) {
                getSupportFragmentManager().beginTransaction().
                        replace(R.id.second_panel, ServiceBrowserFragment.newInstance(serviceDomain, serviceRegType)).commit();
                Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.third_panel);
                if (fragment != null) {
                    getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                }
                this.regType = serviceRegType;
                this.serviceName = null;
                updateNavigation();
            }
            else{
                Intent intent = RegTypeActivity.createIntent(this, serviceRegType, serviceDomain);
                startActivity(intent);
            }
        }
        else{
            ServiceDetailFragment fragment = ServiceDetailFragment.newInstance(service);
            getSupportFragmentManager().beginTransaction().replace(R.id.third_panel, fragment).commit();
            slidingPanelLayout.closePane();
            this.serviceName = service.getServiceName();
            updateNavigation();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(PARAM_DOMAIN, domain);
        outState.putString(PARAM_REG_TYPE, regType);
        outState.putString(PARAM_SERVICE_NAME, serviceName);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onServiceUpdated(BonjourService service) {
        lastUpdatedTextView.setText(getString(R.string.last_update, System.currentTimeMillis()));
    }

    @Override
    public void onServiceStopped(BonjourService service) {
        //Ignore this
    }

    @Override
    public void onHttpServerFound(URL url) {
        // TODO: show FAB
    }
}

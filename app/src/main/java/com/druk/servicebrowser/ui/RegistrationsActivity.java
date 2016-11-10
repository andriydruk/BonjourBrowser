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

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.R;
import com.druk.servicebrowser.ui.adapter.ServiceAdapter;
import com.github.druk.rxdnssd.BonjourService;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.List;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

public class RegistrationsActivity extends AppCompatActivity {

    public static void startActivity(Context context) {
        context.startActivity(new Intent(context, RegistrationsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.blank_activity);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.content, new RegistrationsFragment()).commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class RegistrationsFragment extends Fragment {

        private static final int REGISTER_REQUEST_CODE = 100;
        private static final int STOP_REQUEST_CODE = 101;

        private ServiceAdapter adapter;
        private Subscription mSubscription;
        private RecyclerView mRecyclerView;
        private View mNoServiceView;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            adapter = new ServiceAdapter(getContext()) {
                @Override
                public void onBindViewHolder(ViewHolder holder, int position) {
                    holder.text1.setText(getItem(position).getServiceName());
                    holder.text2.setText(getItem(position).getRegType());
                    holder.itemView.setOnClickListener(v ->
                            startActivityForResult(ServiceActivity.startActivity(getContext(), getItem(position), true), STOP_REQUEST_CODE));
                }
            };
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == REGISTER_REQUEST_CODE) {
                if (resultCode == Activity.RESULT_OK) {
                    BonjourService bonjourService = RegisterServiceActivity.parseResult(data);
                    mSubscription = BonjourApplication.getRegistrationManager(getContext())
                            .register(getContext(), bonjourService)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(service -> {
                                updateServices();
                            }, throwable -> {
                                Toast.makeText(getContext(), "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                }
                return;
            }
            else if (requestCode == STOP_REQUEST_CODE) {
                if (resultCode == Activity.RESULT_OK) {
                    BonjourService bonjourService = ServiceActivity.parseResult(data);
                    BonjourApplication.getRegistrationManager(getContext()).unregister(bonjourService);
                    updateServices();
                }
                return;
            }
            super.onActivityResult(requestCode, resultCode, data);
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_registrations, container, false);
            mRecyclerView = (RecyclerView) view.findViewById(R.id.recycler_view);
            mRecyclerView.setLayoutManager(new LinearLayoutManager(view.getContext(), LinearLayoutManager.VERTICAL, false));
            mRecyclerView.setAdapter(adapter);
            mNoServiceView = view.findViewById(R.id.no_service);
            view.findViewById(R.id.fab).setOnClickListener(v ->
                    startActivityForResult(RegisterServiceActivity.createIntent(getContext()), REGISTER_REQUEST_CODE));
            updateServices();
            return view;
        }

        @Override
        public void onStop() {
            super.onStop();
            if (mSubscription != null && !mSubscription.isUnsubscribed()) {
                mSubscription.unsubscribe();
            }
        }

        private void updateServices() {
            List<BonjourService> registeredServices = BonjourApplication.getRegistrationManager(getContext()).getRegisteredServices();
            adapter.swap(registeredServices);
            mNoServiceView.setVisibility(registeredServices.size() > 0 ? View.GONE : View.VISIBLE);
        }
    }
}

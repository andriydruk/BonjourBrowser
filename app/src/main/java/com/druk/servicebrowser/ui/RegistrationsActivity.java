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
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.R;
import com.druk.servicebrowser.ui.adapter.ServiceAdapter;
import com.github.druk.rx2dnssd.BonjourService;

import java.util.List;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;

public class RegistrationsActivity extends AppCompatActivity {

    public static void startActivity(Context context) {
        context.startActivity(new Intent(context, RegistrationsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.blank_activity);
        setSupportActionBar(findViewById(R.id.toolbar));
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
        private Disposable mDisposable;
        private RecyclerView mRecyclerView;
        private View mNoServiceView;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            adapter = new ServiceAdapter(getContext()) {
                @Override
                public void onBindViewHolder(ViewHolder holder, final int position) {
                    holder.text1.setText(getItem(position).getServiceName());
                    holder.text2.setText(getItem(position).getRegType());
                    holder.itemView.setOnClickListener(v -> startActivityForResult(ServiceActivity.startActivity(getContext(), getItem(position), true), STOP_REQUEST_CODE));
                }
            };
        }

        @Override
        public void onActivityResult(int requestCode, int resultCode, Intent data) {
            if (requestCode == REGISTER_REQUEST_CODE) {
                if (resultCode == Activity.RESULT_OK) {
                    BonjourService bonjourService = RegisterServiceActivity.parseResult(data);
                    mDisposable = BonjourApplication.getRegistrationManager(getContext())
                            .register(getContext(), bonjourService)
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(service -> RegistrationsFragment.this.updateServices(), throwable -> Toast.makeText(RegistrationsFragment.this.getContext(), "Error: " + throwable.getMessage(), Toast.LENGTH_SHORT).show());
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
            mRecyclerView = view.findViewById(R.id.recycler_view);
            mRecyclerView.setLayoutManager(new LinearLayoutManager(view.getContext(), LinearLayoutManager.VERTICAL, false));
            mRecyclerView.setAdapter(adapter);
            mNoServiceView = view.findViewById(R.id.no_service);
            view.findViewById(R.id.fab).setOnClickListener(v -> RegistrationsFragment.this.startActivityForResult(RegisterServiceActivity.createIntent(getContext()), REGISTER_REQUEST_CODE));
            updateServices();
            return view;
        }

        @Override
        public void onStop() {
            super.onStop();
            if (mDisposable != null && !mDisposable.isDisposed()) {
                mDisposable.dispose();
            }
        }

        private void updateServices() {
            List<BonjourService> registeredServices = BonjourApplication.getRegistrationManager(getContext()).getRegisteredServices();
            adapter.swap(registeredServices);
            mNoServiceView.setVisibility(registeredServices.size() > 0 ? View.GONE : View.VISIBLE);
        }
    }
}

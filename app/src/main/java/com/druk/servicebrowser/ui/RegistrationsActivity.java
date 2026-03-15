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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.BonjourServiceInfo;
import com.druk.servicebrowser.R;
import com.druk.servicebrowser.RegistrationManager;
import com.druk.servicebrowser.ui.adapter.ServiceAdapter;

import java.util.List;

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
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    public static class RegistrationsFragment extends Fragment {

        private ServiceAdapter adapter;
        private RecyclerView mRecyclerView;
        private View mNoServiceView;

        private ActivityResultLauncher<Intent> registerLauncher;
        private ActivityResultLauncher<Intent> stopLauncher;

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);

            registerLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            BonjourServiceInfo bonjourServiceInfo = RegisterServiceActivity.parseResult(result.getData());
                            BonjourApplication.getRegistrationManager(getContext())
                                    .register(bonjourServiceInfo, new RegistrationManager.RegistrationCallback() {
                                        @Override
                                        public void onServiceRegistered(BonjourServiceInfo service) {
                                            if (getActivity() != null) {
                                                getActivity().runOnUiThread(() -> updateServices());
                                            }
                                        }

                                        @Override
                                        public void onRegistrationFailed(int errorCode) {
                                            if (getActivity() != null) {
                                                getActivity().runOnUiThread(() ->
                                                        Toast.makeText(getContext(), "Error: registration failed (" + errorCode + ")", Toast.LENGTH_SHORT).show());
                                            }
                                        }
                                    });
                        }
                    });

            stopLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            BonjourServiceInfo bonjourServiceInfo = ServiceActivity.parseResult(result.getData());
                            BonjourApplication.getRegistrationManager(getContext()).unregister(bonjourServiceInfo);
                            updateServices();
                        }
                    });

            adapter = new ServiceAdapter(getContext()) {
                @Override
                public void onBindViewHolder(ViewHolder holder, final int position) {
                    holder.text1.setText(getItem(position).getServiceName());
                    holder.text2.setText(getItem(position).getRegType());
                    holder.itemView.setOnClickListener(v -> stopLauncher.launch(ServiceActivity.startActivity(getContext(), getItem(position), true)));
                }
            };
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_registrations, container, false);
            mRecyclerView = view.findViewById(R.id.recycler_view);
            mRecyclerView.setLayoutManager(new LinearLayoutManager(view.getContext(), LinearLayoutManager.VERTICAL, false));
            mRecyclerView.setAdapter(adapter);
            mNoServiceView = view.findViewById(R.id.no_service);
            view.findViewById(R.id.fab).setOnClickListener(v -> registerLauncher.launch(RegisterServiceActivity.createIntent(getContext())));
            updateServices();
            return view;
        }

        private void updateServices() {
            List<BonjourServiceInfo> registeredServices = BonjourApplication.getRegistrationManager(getContext()).getRegisteredServices();
            adapter.swap(registeredServices);
            mNoServiceView.setVisibility(registeredServices.size() > 0 ? View.GONE : View.VISIBLE);
        }
    }
}

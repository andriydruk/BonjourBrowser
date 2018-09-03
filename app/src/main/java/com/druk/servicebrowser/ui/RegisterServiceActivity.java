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
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.util.ArrayMap;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatAutoCompleteTextView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.R;
import com.druk.servicebrowser.ui.adapter.TxtRecordsAdapter;
import com.github.druk.rxdnssd.BonjourService;

import java.util.HashMap;
import java.util.List;

public class RegisterServiceActivity extends AppCompatActivity {

    private static final String SERVICE = "service";

    public static Intent createIntent(Context context) {
        return new Intent(context, RegisterServiceActivity.class);
    }

    public static BonjourService parseResult(Intent intent) {
        return intent.getParcelableExtra(SERVICE);
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
            getSupportFragmentManager().beginTransaction().replace(R.id.content, new RegisterServiceFragment()).commit();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private void setResult(BonjourService bonjourService) {
        setResult(Activity.RESULT_OK, new Intent().putExtra(SERVICE, bonjourService));
        finish();
    }

    public static class RegisterServiceFragment extends Fragment implements TextView.OnEditorActionListener, View.OnClickListener {

        private EditText serviceNameEditText;
        private AppCompatAutoCompleteTextView regTypeEditText;
        private EditText portEditText;
        private TxtRecordsAdapter adapter;
        private final ArrayMap<String, String> mRecords = new ArrayMap<>();

        @Override
        public void onAttach(Context context) {
            super.onAttach(context);
            setHasOptionsMenu(true);
        }

        @Nullable
        @Override
        public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
            View view = inflater.inflate(R.layout.fragment_register_service, container, false);
            serviceNameEditText = view.findViewById(R.id.service_name);
            regTypeEditText = view.findViewById(R.id.reg_type);
            portEditText = view.findViewById(R.id.port);

            serviceNameEditText.setOnEditorActionListener(this);
            regTypeEditText.setOnEditorActionListener(this);
            portEditText.setOnEditorActionListener(this);

            adapter = new TxtRecordsAdapter(getContext(), new HashMap<>()){

                @Override
                public void onItemClick(View view, int position) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    final String key = getKey(position);
                    String value = getValue(position);
                    // Inflate and set the layout for the dialog
                    // Pass null as the parent view because its going in the dialog layout
                    builder.setMessage("Do you really want to delete " + key + "=" + value + " ?")
                            .setPositiveButton(android.R.string.ok, (dialog, id1) -> {
                                mRecords.remove(key);
                                adapter.swap(mRecords);
                                adapter.notifyDataSetChanged();
                            })
                            .setNegativeButton(android.R.string.cancel, (dialog, id1) -> {

                            });
                    builder.create().show();
                }
            };

            RecyclerView recyclerView = view.findViewById(R.id.recycler_view);
            recyclerView.setLayoutManager(new LinearLayoutManager(view.getContext(), LinearLayoutManager.VERTICAL, false));
            recyclerView.setAdapter(adapter);

            List<String> regTypes = BonjourApplication.getRegTypeManager(getContext()).getListRegTypes();
            regTypeEditText.setAdapter(new ArrayAdapter<>(getContext(), android.R.layout.select_dialog_item, regTypes));

            view.findViewById(R.id.fab).setOnClickListener(this);

            return view;
        }

        public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
            inflater.inflate(R.menu.menu_registered_services, menu);
        }

        @Override
        public boolean onOptionsItemSelected(MenuItem item) {
            // Handle action bar item clicks here. The action bar will
            // automatically handle clicks on the Home/Up button, so long
            // as you specify a parent activity in AndroidManifest.xml.
            int id = item.getItemId();

            //noinspection SimplifiableIfStatement
            if (id == R.id.action_add) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                View view = getActivity().getLayoutInflater().inflate(R.layout.dialog_add_txt_records, null);
                final TextView keyTextView = view.findViewById(R.id.key);
                final TextView valueTextView = view.findViewById(R.id.value);
                // Inflate and set the layout for the dialog
                // Pass null as the parent view because its going in the dialog layout
                builder.setMessage("Add TXT record")
                        .setView(view)
                        .setPositiveButton(android.R.string.ok, (dialog, id1) -> {
                            mRecords.put(keyTextView.getText().toString(), valueTextView.getText().toString());
                            adapter.swap(mRecords);
                            adapter.notifyDataSetChanged();
                        })
                        .setNegativeButton(android.R.string.cancel, (dialog, id1) -> {

                        });
                builder.create().show();
                return true;
            }

            return super.onOptionsItemSelected(item);
        }

        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (getView() == null || actionId != EditorInfo.IME_ACTION_DONE) {
                return false;
            }
            switch (v.getId()) {
                case R.id.service_name:
                    regTypeEditText.requestFocus();
                    return true;
                case R.id.reg_type:
                    portEditText.requestFocus();
                    return true;
                case R.id.port:
                    InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(getView().getWindowToken(), 0);
                    return true;
            }
            return false;
        }

        @Override
        public void onClick(View v) {
            if (getView() == null) {
                return;
            }
            String serviceName = serviceNameEditText.getText().toString();
            String reqType = regTypeEditText.getText().toString();
            String port = portEditText.getText().toString();
            Integer portNumber = 0;

            boolean isValid = true;
            if (TextUtils.isEmpty(serviceName)) {
                isValid = false;
                serviceNameEditText.setError("Service name can't be unspecified");
            }
            if (TextUtils.isEmpty(reqType)) {
                isValid = false;
                regTypeEditText.setError("Reg type can't be unspecified");
            }
            if (TextUtils.isEmpty(port)) {
                isValid = false;
                portEditText.setError("Port can't be unspecified");
            } else {
                try {
                    portNumber = Integer.parseInt(port);
                    if (portNumber < 0 || portNumber > 65535) {
                        isValid = false;
                        portEditText.setError("Invalid port number (0-65535)");
                    }
                } catch (NumberFormatException e) {
                    isValid = false;
                    portEditText.setError("Invalid port number (0-65535)");
                }
            }

            if (isValid) {
                if (getActivity() instanceof RegisterServiceActivity) {
                    ((RegisterServiceActivity) getActivity()).setResult(
                            new BonjourService.Builder(0, 0, serviceName, reqType, null).port(portNumber).dnsRecords(mRecords).build());
                }
            }
        }
    }
}

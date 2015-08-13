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

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatTextView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class LicensesActivity extends AppCompatActivity implements View.OnClickListener {

    private static String[] LICENSE_SOFTWARE = new String[]{"Android Compatibility Library v4", "Android Compatibility Library v7", "Android Design Support Library", "Android SDK", "mDNSResponder", "RxAndroid"};

    private static final String ANDROID_ASSETS_FILE_PATH = "file:///android_asset/";
    private static final String ANDROID_OPEN_SOURCE_PROJECT_LICENSE = "ANDROID-OPEN-SOURCE-PROJECT-LICENSE.txt";
    private static final String ANDROID_SOFTWARE_DEVELOPMENT_KIT = "ANDROID-SOFTWARE-DEVELOPMENT-KIT.txt";
    private static final String APACHE_LICENSE = "APACHE-LICENSE-2.0.txt";

    private LinearLayoutManager mLayoutManager;
    private OpenSourceComponentAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        mLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mAdapter = new OpenSourceComponentAdapter(this, LICENSE_SOFTWARE, new String[]{
                ANDROID_ASSETS_FILE_PATH + ANDROID_OPEN_SOURCE_PROJECT_LICENSE,
                ANDROID_ASSETS_FILE_PATH + ANDROID_OPEN_SOURCE_PROJECT_LICENSE,
                ANDROID_ASSETS_FILE_PATH + ANDROID_OPEN_SOURCE_PROJECT_LICENSE,
                ANDROID_ASSETS_FILE_PATH + ANDROID_SOFTWARE_DEVELOPMENT_KIT,
                ANDROID_ASSETS_FILE_PATH + APACHE_LICENSE,
                ANDROID_ASSETS_FILE_PATH + APACHE_LICENSE
        });

        RecyclerView recyclerView = ((RecyclerView) findViewById(R.id.recycler_view));
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.setListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAdapter.setListener(null);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onClick(View v) {
        int position = mLayoutManager.getPosition(v);
        final Intent intent = new Intent(v.getContext(), HTMLViewerActivity.class);
        intent.setData(Uri.parse(mAdapter.getLicensePath(position)));
        intent.putExtra(Intent.EXTRA_TITLE, mAdapter.getComponentName(position));
        intent.addCategory(Intent.CATEGORY_DEFAULT);

        try {
            v.getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Log.e("TAG", "Failed to find viewer", e);
        }
    }

    private static class OpenSourceComponentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final int mBackground;
        private String[] componentNames;
        private String[] licensePaths;

        private View.OnClickListener listener;

        private OpenSourceComponentAdapter(Context context, String[] names, String[] paths) {
            this.componentNames = names;
            this.licensePaths = paths;
            TypedValue mTypedValue = new TypedValue();
            context.getTheme().resolveAttribute(R.attr.selectableItemBackground, mTypedValue, true);
            mBackground = mTypedValue.resourceId;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
            RecyclerView.ViewHolder vh = new RecyclerView.ViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.one_text_item, viewGroup, false)) {
            };
            vh.itemView.setBackgroundResource(mBackground);
            return vh;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
            ((AppCompatTextView) viewHolder.itemView).setText(componentNames[i]);
            viewHolder.itemView.setOnClickListener(listener);
        }

        @Override
        public int getItemCount() {
            return componentNames.length;
        }

        public void setListener(View.OnClickListener listener) {
            this.listener = listener;
        }

        public String getComponentName(int position) {
            return componentNames[position];
        }

        public String getLicensePath(int position) {
            return licensePaths[position];
        }
    }
}

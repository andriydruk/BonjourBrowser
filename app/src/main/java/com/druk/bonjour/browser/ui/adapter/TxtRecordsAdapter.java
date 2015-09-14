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
package com.druk.bonjour.browser.ui.adapter;

import com.druk.bonjour.browser.R;
import com.druk.bonjour.browser.databinding.TxtRecordItemBinding;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.design.widget.Snackbar;
import android.support.v4.util.ArrayMap;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Map;
import java.util.TreeMap;

public class TxtRecordsAdapter extends RecyclerView.Adapter<TxtRecordsAdapter.ViewHolder> {

    private final int mBackground;
    private final ArrayMap<String, String> mRecords = new ArrayMap<>();

    public TxtRecordsAdapter(Context context, Map<String, String> records) {
        TypedValue mTypedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.selectableItemBackground, mTypedValue, true);
        mBackground = mTypedValue.resourceId;
        mRecords.putAll(records);
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.txt_record_item, viewGroup, false);
        view.setBackgroundResource(mBackground);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        holder.mBinding.setName(getKey(position));
        holder.mBinding.setValue(getValue(position));
        holder.itemView.setOnClickListener(v -> {
            Context context = v.getContext();
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(getKey(position), getValue(position));
            clipboard.setPrimaryClip(clip);

            Snackbar.make(v, context.getResources().getString(R.string.copy_toast_message, getKey(position)), Snackbar.LENGTH_LONG).show();
        });
    }

    @Override
    public int getItemCount() {
        return mRecords.size();
    }

    public String getKey(int position) {
        return mRecords.keyAt(position);
    }

    public String getValue(int position) {
        return mRecords.valueAt(position);
    }

    public void swap(Map<String, String> records) {
        this.mRecords.clear();
        this.mRecords.putAll(new TreeMap<>(records));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TxtRecordItemBinding mBinding;

        public ViewHolder(View itemView) {
            super(itemView);
            mBinding = TxtRecordItemBinding.bind(itemView);
        }
    }
}

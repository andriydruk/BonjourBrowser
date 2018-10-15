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
package com.druk.servicebrowser.ui.adapter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.support.design.widget.Snackbar;
import android.support.v4.util.ArrayMap;
import android.support.v4.util.SimpleArrayMap;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.druk.servicebrowser.R;


public class TxtRecordsAdapter extends RecyclerView.Adapter<TxtRecordsAdapter.ViewHolder> {

    private final int mBackground;
    private final SimpleArrayMap<String, String> ipRecords = new SimpleArrayMap<>();
    private final SimpleArrayMap<String, String> txtRecords = new SimpleArrayMap<>();

    public TxtRecordsAdapter(Context context) {
        TypedValue mTypedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.selectableItemBackground, mTypedValue, true);
        mBackground = mTypedValue.resourceId;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.two_text_item, viewGroup, false);
        view.setBackgroundResource(mBackground);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, final int position) {
        holder.text1.setText(getKey(position));
        holder.text2.setText(getValue(position));
        holder.itemView.setOnClickListener(v -> TxtRecordsAdapter.this.onItemClick(v, position));
    }

    public void onItemClick(View view, int position){
        Context context = view.getContext();

        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getKey(position), getValue(position));
        clipboard.setPrimaryClip(clip);

        Snackbar snackbar = Snackbar.make(view, context.getResources().getString(R.string.copy_toast_message, getKey(position)), Snackbar.LENGTH_LONG);
        snackbar.getView().setBackgroundResource(R.color.accent);
        snackbar.show();
    }

    @Override
    public int getItemCount() {
        return ipRecords.size() + txtRecords.size();
    }

    protected String getKey(int position) {
        if (position < ipRecords.size()) {
            return ipRecords.keyAt(position);
        }
        else {
            return txtRecords.keyAt(position - ipRecords.size());
        }
    }

    protected String getValue(int position) {
        if (position < ipRecords.size()) {
            return ipRecords.valueAt(position);
        }
        else {
            return txtRecords.valueAt(position - ipRecords.size());
        }
    }

    public void swapIPRecords(ArrayMap<String, String> records) {
        this.ipRecords.clear();
        this.ipRecords.putAll(records);
    }

    public void swapTXTRecords(ArrayMap<String, String> records) {
        this.txtRecords.clear();
        this.txtRecords.putAll(records);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TextView text1;
        public TextView text2;

        ViewHolder(View itemView) {
            super(itemView);
            text1 = itemView.findViewById(R.id.text1);
            text2 = itemView.findViewById(R.id.text2);
        }
    }
}

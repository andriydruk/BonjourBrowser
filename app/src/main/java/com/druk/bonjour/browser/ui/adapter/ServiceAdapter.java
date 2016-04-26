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
import com.druk.bonjour.browser.databinding.TwoTextItemBinding;
import com.github.druk.rxdnssd.BonjourService;

import android.content.Context;
import android.databinding.DataBindingUtil;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class ServiceAdapter extends RecyclerView.Adapter<ServiceAdapter.ViewHolder> {

    private final int mSelectedBackground;
    private final int mBackground;
    private final ArrayList<BonjourService> services = new ArrayList<>();

    private long mSelectedItemId = -1;

    public ServiceAdapter(Context context) {
        TypedValue mTypedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.selectableItemBackground, mTypedValue, true);
        mBackground = mTypedValue.resourceId;

        context.getTheme().resolveAttribute(R.attr.selectedItemBackground, mTypedValue, true);
        mSelectedBackground = mTypedValue.resourceId;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        return new ViewHolder(LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.two_text_item, viewGroup, false));
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    @Override
    public long getItemId(int position) {
        return services.get(position).hashCode();
    }

    public BonjourService getItem(int position) {
        return services.get(position);
    }

    public void clear() {
        this.services.clear();
    }

    public long getSelectedItemId() {
        return mSelectedItemId;
    }

    public void setSelectedItemId(long selectedPosition) {
        mSelectedItemId = selectedPosition;
    }

    public int getBackground(int position){
        return (getItemId(position) == mSelectedItemId) ? mSelectedBackground : mBackground;
    }

    public void add(BonjourService service) {
        this.services.remove(service);
        this.services.add(service);
        Collections.sort(services, (lhs, rhs) -> lhs.getServiceName().compareTo(rhs.getServiceName()));
    }

    public void swap(List<BonjourService> service) {
        this.services.clear();
        this.services.addAll(service);
        Collections.sort(services, (lhs, rhs) -> lhs.getServiceName().compareTo(rhs.getServiceName()));
        notifyDataSetChanged();
    }

    public void remove(BonjourService bonjourService) {
        if (this.services.remove(bonjourService)) {
            Collections.sort(services, (lhs, rhs) -> lhs.getServiceName().compareTo(rhs.getServiceName()));
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public TwoTextItemBinding binding;

        public ViewHolder(View itemView) {
            super(itemView);
            binding = DataBindingUtil.bind(itemView);
        }
    }
}

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
import com.druk.bonjour.browser.dnssd.BonjourService;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;

public class ServiceAdapter<VH extends ServiceAdapter.ViewHolder> extends RecyclerView.Adapter<VH> {

    private final int mBackground;
    private final ArrayList<BonjourService> services = new ArrayList<>();
    private final ViewHolderCreator<VH> mViewHolderCreator;
    private final View.OnClickListener mListener;

    public ServiceAdapter(Context context, ViewHolderCreator<VH> viewHolderCreator, View.OnClickListener listener) {
        TypedValue mTypedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.selectableItemBackground, mTypedValue, true);
        mBackground = mTypedValue.resourceId;
        mViewHolderCreator = viewHolderCreator;
        mListener = listener;
    }

    @Override
    public VH onCreateViewHolder(ViewGroup parent, int viewType) {
        VH viewHolder = mViewHolderCreator.bind(parent);
        viewHolder.itemView.setBackgroundResource(mBackground);
        viewHolder.itemView.setOnClickListener(mListener);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(VH holder, int position) {
        holder.setService(getItem(position));
    }

    @Override
    public int getItemCount() {
        return services.size();
    }

    public BonjourService getItem(int position) {
        return services.get(position);
    }

    public void clear() {
        this.services.clear();
    }

    public void add(BonjourService service) {
        this.services.remove(service);
        this.services.add(service);
        //Collections.sort(services, (lhs, rhs) -> lhs.serviceName.compareTo(rhs.serviceName));
    }

    public void remove(BonjourService bonjourService) {
        if (this.services.remove(bonjourService)) {
            //Collections.sort(services, (lhs, rhs) -> lhs.serviceName.compareTo(rhs.serviceName));
        }
    }

    public static abstract class ViewHolder extends RecyclerView.ViewHolder{

        public ViewHolder(View itemView) {
            super(itemView);
        }

        abstract public void setService(BonjourService service);
    }

    public interface ViewHolderCreator<VH>{
        VH bind(ViewGroup parent);
    }
}

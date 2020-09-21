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
package com.druk.servicebrowser.ui.fragment;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.Config;
import com.druk.servicebrowser.R;
import com.druk.servicebrowser.RegTypeManager;
import com.druk.servicebrowser.ui.adapter.ServiceAdapter;
import com.druk.servicebrowser.ui.viewmodel.RegTypeBrowserViewModel;
import com.github.druk.rx2dnssd.BonjourService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import io.reactivex.functions.Consumer;

import static com.druk.servicebrowser.Config.EMPTY_DOMAIN;


public class RegTypeBrowserFragment extends ServiceBrowserFragment {

    private static final String TAG = "RegTypeBrowser";

    private RegTypeManager mRegTypeManager;

    public static Fragment newInstance(String regType) {
        return fillArguments(new RegTypeBrowserFragment(), EMPTY_DOMAIN, regType);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRegTypeManager = BonjourApplication.getRegTypeManager(getContext());
        mAdapter = new ServiceAdapter(getActivity()) {

            Drawable drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_star_accent);

            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int i) {
                RegTypeBrowserViewModel.BonjourDomain domain = (RegTypeBrowserViewModel.BonjourDomain) getItem(i);
                String regType = domain.getServiceName() + "." + domain.getRegType().split(Config.REG_TYPE_SEPARATOR)[0] + ".";
                String regTypeDescription = mRegTypeManager.getRegTypeDescription(regType);
                if (regTypeDescription != null) {
                    viewHolder.text1.setText(regType + " (" + regTypeDescription + ")");
                } else {
                    viewHolder.text1.setText(regType);
                }

                if (favouritesManager.isFavourite(regType)) {
                    viewHolder.text1.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null);
                }
                else {
                    viewHolder.text1.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                }

                viewHolder.text2.setText(domain.serviceCount + " services");
                viewHolder.itemView.setOnClickListener(mListener);
                viewHolder.itemView.setBackgroundResource(getBackground(i));
            }

            @Override
            public void sortServices(ArrayList<BonjourService> services) {
                Collections.sort(services, (lhs, rhs) -> {
                    String lhsRegType = lhs.getServiceName() + "." + lhs.getRegType().split(Config.REG_TYPE_SEPARATOR)[0] + ".";
                    String rhsRegType = rhs.getServiceName() + "." + rhs.getRegType().split(Config.REG_TYPE_SEPARATOR)[0] + ".";
                    boolean isLhsFavourite = favouritesManager.isFavourite(lhsRegType);
                    boolean isRhsFavourite = favouritesManager.isFavourite(rhsRegType);
                    if (isLhsFavourite && isRhsFavourite) {
                        return lhs.getServiceName().compareTo(rhs.getServiceName());
                    }
                    else if (isLhsFavourite) {
                        return -1;
                    }
                    else if (isRhsFavourite) {
                        return 1;
                    }
                    return lhs.getServiceName().compareTo(rhs.getServiceName());
                });
            }
        };
    }

    @Override
    protected void createViewModel() {
        RegTypeBrowserViewModel viewModel = new ViewModelProvider.AndroidViewModelFactory(BonjourApplication.getApplication(requireContext()))
                .create(RegTypeBrowserViewModel.class);

        final Consumer<Throwable> errorAction = throwable -> {
            Log.e("DNSSD", "Error: ", throwable);
            RegTypeBrowserFragment.this.showError(throwable);
        };

        final Consumer<Collection<RegTypeBrowserViewModel.BonjourDomain>> servicesAction = services -> {
            final int itemsCount = mAdapter.getItemCount();
            mAdapter.clear();
            for (RegTypeBrowserViewModel.BonjourDomain bonjourDomain: services) {
                if (bonjourDomain.serviceCount > 0) {
                    mAdapter.add(bonjourDomain);
                }
            }
            RegTypeBrowserFragment.this.showList();
            mAdapter.notifyDataSetChanged();
        };

        viewModel.startDiscovery(servicesAction, errorAction);
    }

    @Override
    protected boolean favouriteMenuSupport() {
        return false;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Favourites can be changed
        mAdapter.sortServices();
        mAdapter.notifyDataSetChanged();
    }
}

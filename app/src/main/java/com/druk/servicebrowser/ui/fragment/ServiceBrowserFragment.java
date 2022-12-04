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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.BuildConfig;
import com.druk.servicebrowser.FavouritesManager;
import com.druk.servicebrowser.R;
import com.druk.servicebrowser.ui.adapter.ServiceAdapter;
import com.druk.servicebrowser.ui.viewmodel.ServiceBrowserViewModel;
import com.github.druk.rx2dnssd.BonjourService;

public class ServiceBrowserFragment extends Fragment {

    private static final String KEY_REG_TYPE = "reg_type";
    private static final String KEY_DOMAIN = "domain";
    private static final String KEY_SELECTED_POSITION = "selected_position";

    protected FavouritesManager favouritesManager;

    protected ServiceAdapter mAdapter;
    protected String mReqType;
    protected String mDomain;
    protected RecyclerView mRecyclerView;
    protected LinearLayout mProgressView;
    protected LinearLayout mErrorView;

    protected View.OnClickListener mListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int position = mRecyclerView.getLayoutManager().getPosition(v);
            mAdapter.setSelectedItemId(mAdapter.getItemId(position));
            mAdapter.notifyDataSetChanged();
            if (ServiceBrowserFragment.this.isAdded()) {
                BonjourService service = mAdapter.getItem(position);
                ((ServiceListener) ServiceBrowserFragment.this.getActivity()).onServiceWasSelected(mDomain, mReqType, service);
            }
        }
    };

    public static Fragment newInstance(String domain, String regType) {
        return fillArguments(new ServiceBrowserFragment(), domain, regType);
    }

    protected static Fragment fillArguments(Fragment fragment, String domain, String regType) {
        Bundle bundle = new Bundle();
        bundle.putString(KEY_DOMAIN, domain);
        bundle.putString(KEY_REG_TYPE, regType);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (!(context instanceof ServiceListener)) {
            throw new IllegalArgumentException("Fragment context should implement ServiceListener interface");
        }

        favouritesManager = BonjourApplication.getFavouritesManager(context);
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (getArguments() != null) {
            mReqType = getArguments().getString(KEY_REG_TYPE);
            mDomain = getArguments().getString(KEY_DOMAIN);
        }

        mAdapter = new ServiceAdapter(getActivity()) {
            @Override
            public void onBindViewHolder(ViewHolder viewHolder, int i) {
                BonjourService service = getItem(i);
                viewHolder.text1.setText(service.getServiceName());
                if (service.getInet4Address() != null) {
                    viewHolder.text2.setText(service.getInet4Address().getHostAddress());
                }
                else if (service.getInet6Address() != null) {
                    viewHolder.text2.setText(service.getInet6Address().getHostAddress());
                }
                else {
                    viewHolder.text2.setText(service.getHostname());
                }
                viewHolder.itemView.setOnClickListener(mListener);
                viewHolder.itemView.setBackgroundResource(getBackground(i));
            }
        };

        createViewModel();
        setHasOptionsMenu(favouriteMenuSupport());
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_domain, menu);
        MenuItem item = menu.findItem(R.id.action_star);
        boolean isFavourite = favouritesManager.isFavourite(mReqType);
        item.setChecked(isFavourite);
        item.setIcon(isFavourite ? R.drawable.ic_star : R.drawable.ic_star_border);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_star) {
            if (!item.isChecked()) {
                favouritesManager.addToFavourites(mReqType);
                item.setChecked(true);
                item.setIcon(R.drawable.ic_star);
                Toast.makeText(getContext(), mReqType + " saved to Favourites", Toast.LENGTH_LONG).show();
            }
            else {
                favouritesManager.removeFromFavourites(mReqType);
                item.setChecked(false);
                item.setIcon(R.drawable.ic_star_border);
                Toast.makeText(getContext(), mReqType + " removed from Favourites", Toast.LENGTH_LONG).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected boolean favouriteMenuSupport() {
        return true;
    }

    protected void createViewModel() {
        ServiceBrowserViewModel viewModel = new ViewModelProvider(this).get(ServiceBrowserViewModel.class);
        viewModel.startDiscovery(mReqType, mDomain, service -> {
            if (!service.isLost()) {
                mAdapter.add(service);
            } else {
                mAdapter.remove(service);
            }
            ServiceBrowserFragment.this.showList();
            mAdapter.notifyDataSetChanged();
        }, throwable -> {
            Log.e("DNSSD", "Error: ", throwable);
            ServiceBrowserFragment.this.showError(throwable);
        });
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FrameLayout rootView = (FrameLayout) inflater.inflate(R.layout.fragment_service_browser, container, false);
        mRecyclerView = rootView.findViewById(R.id.recycler_view);
        mProgressView = rootView.findViewById(R.id.progress);
        mErrorView = rootView.findViewById(R.id.error_container);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mRecyclerView.getContext()));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        if (savedInstanceState != null) {
            mAdapter.setSelectedItemId(savedInstanceState.getLong(KEY_SELECTED_POSITION, -1L));
        }
        return rootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_SELECTED_POSITION, mAdapter.getSelectedItemId());
    }

    protected void showList(){
        if (mAdapter.getItemCount() > 0) {
            mRecyclerView.setVisibility(View.VISIBLE);
            mProgressView.setVisibility(View.GONE);
        }
        else {
            mRecyclerView.setVisibility(View.GONE);
            mProgressView.setVisibility(View.VISIBLE);
        }
    }

    protected void showError(final Throwable e){
        if (BuildConfig.BUILD_TYPE.equals("iot")) {
            Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e);
            return;
        }
        getActivity().runOnUiThread(() -> {
            mRecyclerView.animate().alpha(0.0f).setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mRecyclerView.setVisibility(View.GONE);
                }
            }).start();
            mProgressView.animate().alpha(0.0f).setInterpolator(new AccelerateDecelerateInterpolator()).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(View.GONE);
                }
            }).start();
            mErrorView.setAlpha(0.0f);
            mErrorView.setVisibility(View.VISIBLE);
            mErrorView.animate().alpha(1.0f).setInterpolator(new AccelerateDecelerateInterpolator()).start();
            mErrorView.findViewById(R.id.send_report).setOnClickListener(v -> Thread.getDefaultUncaughtExceptionHandler().uncaughtException(Thread.currentThread(), e));
        });
    }

    public interface ServiceListener {
        void onServiceWasSelected(String domain, String regType, BonjourService service);
    }
}

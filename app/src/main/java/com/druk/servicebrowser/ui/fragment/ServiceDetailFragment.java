package com.druk.servicebrowser.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.core.os.BundleCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.druk.servicebrowser.BonjourServiceInfo;
import com.druk.servicebrowser.R;
import com.druk.servicebrowser.ui.adapter.TxtRecordsAdapter;
import com.druk.servicebrowser.ui.viewmodel.ServiceDetailViewModel;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;

public class ServiceDetailFragment extends Fragment implements View.OnClickListener {

    private static final String KEY_SERVICE = "com.druk.servicebrowser.ui.fragment.ServiceDetailFragment.key_service";

    private BonjourServiceInfo mService;
    private ServiceDetailViewModel viewModel;

    private TxtRecordsAdapter mAdapter;

    public static ServiceDetailFragment newInstance(BonjourServiceInfo service) {
        ServiceDetailFragment fragment = new ServiceDetailFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_SERVICE, service);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        if (!(context instanceof ServiceDetailListener)) {
            throw new IllegalArgumentException("Fragment context should implement ServiceDetailListener interface");
        }
    }

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mService = BundleCompat.getParcelable(getArguments(), KEY_SERVICE, BonjourServiceInfo.class);
        }
        mAdapter = new TxtRecordsAdapter();

        viewModel = new ViewModelProvider(this).get(ServiceDetailViewModel.class);
        viewModel.getServiceInfoLiveData().observe(this, service -> {
            mService = service;
            updateRecords(service);
        });
        viewModel.getHttpUrlLiveData().observe(this, this::onHttpServerFound);
        viewModel.resolve(mService);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        RecyclerView mRecyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_service_detail, container, false);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mRecyclerView.getContext()));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        updateRecords(mService);
        ((ServiceDetailListener) getActivity()).onServiceUpdated(mService);
        return mRecyclerView;
    }

    private void updateRecords(BonjourServiceInfo service) {
        ArrayMap<String, String> ipInfo = new ArrayMap<>();
        for (InetAddress inetAddress : service.getInetAddresses()) {
            if (inetAddress instanceof Inet4Address) {
                ipInfo.put("Address IPv4", inetAddress.getHostAddress() + ":" + service.getPort());
            } else {
                ipInfo.put("Address IPv6", inetAddress.getHostAddress() + ":" + service.getPort());
            }
        }
        mAdapter.swapIPRecords(ipInfo);

        ArrayMap<String, String> txtInfo = new ArrayMap<>();
        txtInfo.putAll(service.getTxtRecords());
        mAdapter.swapTXTRecords(txtInfo);

        mAdapter.notifyDataSetChanged();
        if (isAdded()) {
            ((ServiceDetailListener) getActivity()).onServiceUpdated(mService);
        }
    }

    private void onHttpServerFound(URL url) {
        ((ServiceDetailListener) getActivity()).onHttpServerFound(url);
    }

    @Override
    public void onClick(View v) {
        ((ServiceDetailListener) getActivity()).onServiceStopped(mService);
    }

    public interface ServiceDetailListener {
        void onServiceUpdated(BonjourServiceInfo service);
        void onHttpServerFound(URL url);
        void onServiceStopped(BonjourServiceInfo service);
    }
}

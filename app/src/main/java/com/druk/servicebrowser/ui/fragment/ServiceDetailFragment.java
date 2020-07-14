package com.druk.servicebrowser.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.R;
import com.druk.servicebrowser.ui.adapter.TxtRecordsAdapter;
import com.druk.servicebrowser.ui.viewmodel.ServiceDetailViewModel;
import com.github.druk.rx2dnssd.BonjourService;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;

public class ServiceDetailFragment extends Fragment implements View.OnClickListener {

    private static final String KEY_SERVICE = "com.druk.servicebrowser.ui.fragment.ServiceDetailFragment.key_service";

    private BonjourService mService;
    private ServiceDetailViewModel viewModel;

    private TxtRecordsAdapter mAdapter;

    public static ServiceDetailFragment newInstance(BonjourService service){
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
            mService = getArguments().getParcelable(KEY_SERVICE);
        }
        mAdapter = new TxtRecordsAdapter(getActivity());

        viewModel = new ViewModelProvider.AndroidViewModelFactory(BonjourApplication.getApplication(requireContext()))
                .create(ServiceDetailViewModel.class);
        viewModel.resolveIPRecords(mService, this::updateIPRecords);
        viewModel.resolveTXTRecords(mService, this::updateTXTRecords);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        RecyclerView mRecyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_service_detail, container, false);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mRecyclerView.getContext()));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        updateIPRecords(mService);
        updateTXTRecords(mService);
        ((ServiceDetailListener)getActivity()).onServiceUpdated(mService);
        return mRecyclerView;
    }

    private void updateIPRecords(BonjourService service) {
        ArrayMap<String, String> metaInfo = new ArrayMap<>();
        for (InetAddress inetAddress : service.getInetAddresses()) {
            if (inetAddress instanceof Inet4Address) {
                metaInfo.put("Address IPv4", service.getInet4Address().getHostAddress() + ":" + service.getPort());
            }
            else {
                metaInfo.put("Address IPv6", service.getInet6Address().getHostAddress() + ":" + service.getPort());
            }
        }
        mAdapter.swapIPRecords(metaInfo);
        mAdapter.notifyDataSetChanged();
        if (isAdded()) {
            ((ServiceDetailListener)getActivity()).onServiceUpdated(mService);
        }

        viewModel.checkHttpConnection(mService, this::onHttpServerFound);
    }

    private void updateTXTRecords(BonjourService service) {
        ArrayMap<String, String> metaInfo = new ArrayMap<>();
        metaInfo.putAll(service.getTxtRecords());
        mAdapter.swapTXTRecords(metaInfo);
        mAdapter.notifyDataSetChanged();
        if (isAdded()) {
            ((ServiceDetailListener)getActivity()).onServiceUpdated(mService);
        }
    }

    private void onHttpServerFound(URL url) {
        ((ServiceDetailListener)getActivity()).onHttpServerFound(url);
    }

    @Override
    public void onClick(View v) {
        ((ServiceDetailListener)getActivity()).onServiceStopped(mService);
    }

    public interface ServiceDetailListener{
        void onServiceUpdated(BonjourService service);
        void onHttpServerFound(URL url);
        void onServiceStopped(BonjourService service);
    }
}
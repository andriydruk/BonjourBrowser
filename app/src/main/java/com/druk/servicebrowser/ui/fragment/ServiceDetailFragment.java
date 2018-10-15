package com.druk.servicebrowser.ui.fragment;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.util.ArrayMap;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.R;
import com.druk.servicebrowser.ui.adapter.TxtRecordsAdapter;
import com.github.druk.rx2dnssd.BonjourService;
import com.github.druk.rx2dnssd.Rx2Dnssd;

import java.net.Inet4Address;
import java.net.InetAddress;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ServiceDetailFragment extends Fragment implements View.OnClickListener {

    private static final String KEY_SERVICE = "com.druk.servicebrowser.ui.fragment.ServiceDetailFragment.key_service";

    private BonjourService mService;
    private Disposable mResolveIPDisposable;
    private Disposable mResolveTXTDisposable;

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

    @Override
    public void onStart() {
        super.onStart();
        Rx2Dnssd mRxDnssd = BonjourApplication.getRxDnssd(getContext());
        mResolveIPDisposable = mRxDnssd.queryIPRecords(mService)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {
                    if (bonjourService.isLost()) {
                        return;
                    }
                    ServiceDetailFragment.this.updateIPRecords(bonjourService);
                }, throwable -> Log.e("DNSSD", "Error: ", throwable));
        mResolveTXTDisposable = mRxDnssd.queryTXTRecords(mService)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {
                    if (bonjourService.isLost()) {
                        return;
                    }
                    ServiceDetailFragment.this.updateTXTRecords(bonjourService);
                }, throwable -> Log.e("DNSSD", "Error: ", throwable));
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mResolveIPDisposable != null) {
            mResolveIPDisposable.dispose();
        }
        if (mResolveTXTDisposable != null) {
            mResolveTXTDisposable.dispose();
        }
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

    @Override
    public void onClick(View v) {
        ((ServiceDetailListener)getActivity()).onServiceStopped(mService);
    }

    public interface ServiceDetailListener{
        void onServiceUpdated(BonjourService service);
        void onServiceStopped(BonjourService service);
    }
}
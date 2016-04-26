package com.druk.bonjour.browser.ui.fragment;

import com.druk.bonjour.browser.BonjourApplication;
import com.druk.bonjour.browser.R;
import com.druk.bonjour.browser.ui.adapter.TxtRecordsAdapter;
import com.github.druk.rxdnssd.BonjourService;
import com.github.druk.rxdnssd.RxDnssd;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.util.ArrayMap;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.Map;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

public class ServiceDetailFragment extends Fragment implements View.OnClickListener {

    private static final String KEY_SERVICE = "com.druk.bonjour.browser.ui.fragment.ServiceDetailFragment.key_service";
    private static final String KEY_REGISTERED = "com.druk.bonjour.browser.ui.fragment.ServiceDetailFragment.key_registered";

    private BonjourService mService;
    private Subscription mResolveSubscription;
    private boolean isRegistered;

    private TxtRecordsAdapter mAdapter;
    private RecyclerView mRecyclerView;

    public static ServiceDetailFragment newInstance(BonjourService service){
        ServiceDetailFragment fragment = new ServiceDetailFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_SERVICE, service);
        fragment.setArguments(bundle);
        return fragment;
    }

    public static ServiceDetailFragment newInstance(BonjourService service, boolean isRegistered){
        ServiceDetailFragment fragment = new ServiceDetailFragment();
        Bundle bundle = new Bundle();
        bundle.putParcelable(KEY_SERVICE, service);
        bundle.putBoolean(KEY_REGISTERED, isRegistered);
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
            isRegistered = getArguments().getBoolean(KEY_REGISTERED, false);
        }
        mAdapter = new TxtRecordsAdapter(getActivity(), new ArrayMap<>());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRecyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_service_detail, container, false);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mRecyclerView.getContext()));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        updateUI(mService, false);
        if (isRegistered){
            resolve(false);
        }
        return mRecyclerView;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mResolveSubscription != null) {
            mResolveSubscription.unsubscribe();
        }
    }

    private void updateUI(BonjourService service, boolean withSnakeBar) {
        Map<String, String> metaInfo = new ArrayMap<>();
        if (service.getInet4Address() != null){
            metaInfo.put("Address IPv4", service.getInet4Address().getHostAddress() + ":" + service.getPort());
        }
        if (service.getInet6Address() != null){
            metaInfo.put("Address IPv6", service.getInet6Address().getHostAddress() + ":" + service.getPort());
        }
        metaInfo.putAll(service.getTxtRecords());
        mAdapter.swap(metaInfo);
        mAdapter.notifyDataSetChanged();

        if (isAdded()){
            ((ServiceDetailListener)getActivity()).onServiceUpdated(service);
            if (withSnakeBar) {
                Snackbar snackbar = Snackbar.make(mRecyclerView, getString(R.string.service_was_resolved), Snackbar.LENGTH_LONG);
                snackbar.getView().setBackgroundResource(R.color.accent);
                snackbar.show();
            }
        }
    }

    @Override
    public void onClick(View v) {
        if (isRegistered){
            ((ServiceDetailListener)getActivity()).onServiceStopped(mService);
            return;
        }
        v.animate().rotationBy(180).start();
        resolve(true);
    }

    private void resolve(boolean withSnakeBar){
        RxDnssd mRxDnssd = BonjourApplication.getRxDnssd(getContext());
        mResolveSubscription = Observable.just(mService)
                .compose(mRxDnssd.resolve())
                .compose(mRxDnssd.queryRecords())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {
                    if (bonjourService.isLost()) {
                        return;
                    }
                    ServiceDetailFragment.this.updateUI(bonjourService, withSnakeBar);
                }, throwable -> {
                    Log.e("DNSSD", "Error: ", throwable);
                });
    }

    public interface ServiceDetailListener{
        void onServiceUpdated(BonjourService service);
        void onServiceStopped(BonjourService service);
    }
}
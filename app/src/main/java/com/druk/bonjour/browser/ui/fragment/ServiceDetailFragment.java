package com.druk.bonjour.browser.ui.fragment;

import com.druk.bonjour.browser.R;
import com.druk.bonjour.browser.dnssd.BonjourService;
import com.druk.bonjour.browser.dnssd.RxDNSSD;
import com.druk.bonjour.browser.ui.adapter.TxtRecordsAdapter;

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

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;

/**
 * Created by Andrew Druk on 9/22/15.
 */
public class ServiceDetailFragment extends Fragment implements View.OnClickListener {

    private static final String KEY_SERVICE = "com.druk.bonjour.browser.ui.fragment.ServiceDetailFragment.key_service";

    private BonjourService mService;
    private Subscription mResolveSubscription;

    private TxtRecordsAdapter mAdapter;
    private RecyclerView mRecyclerView;

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
        mAdapter = new TxtRecordsAdapter(getActivity(), new ArrayMap<>());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRecyclerView = (RecyclerView) inflater.inflate(R.layout.fragment_service_browser, container, false);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mRecyclerView.getContext()));
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(mAdapter);
        updateUI(mService, false);
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
        mAdapter.swap(service.dnsRecords);
        mAdapter.notifyDataSetChanged();

        if (isAdded()){
            ((ServiceDetailListener)getActivity()).onServiceUpdated(service);
        }

        if (withSnakeBar) {
            Snackbar snackbar = Snackbar.make(mRecyclerView, getString(R.string.service_was_resolved), Snackbar.LENGTH_LONG);
            snackbar.getView().setBackgroundResource(R.color.accent);
            snackbar.show();
        }
    }

    @Override
    public void onClick(View v) {
        v.animate().rotationBy(180).start();
        mResolveSubscription = Observable.just(mService)
                .compose(RxDNSSD.resolve())
                .compose(RxDNSSD.queryRecords())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {
                    if ((bonjourService.flags & BonjourService.DELETED) == BonjourService.DELETED) {
                        return;
                    }
                    updateUI(bonjourService, true);
                }, throwable -> {
                    Log.e("DNSSD", "Error: ", throwable);
                });
    }

    public interface ServiceDetailListener{
        void onServiceUpdated(BonjourService service);
    }
}
package com.druk.servicebrowser.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.druk.servicebrowser.BonjourApplication;
import com.github.druk.rx2dnssd.BonjourService;
import com.github.druk.rx2dnssd.Rx2Dnssd;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class ServiceBrowserViewModel extends AndroidViewModel {

    protected Rx2Dnssd mRxDnssd;
    protected Disposable mDisposable;

    public ServiceBrowserViewModel(@NonNull Application application) {
        super(application);
        mRxDnssd = BonjourApplication.getRxDnssd(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mDisposable != null) {
            mDisposable.dispose();
        }
    }

    public void startDiscovery(String reqType,
                               String domain,
                               Consumer<BonjourService> servicesAction,
                               Consumer<Throwable> errorAction) {
        mDisposable = mRxDnssd.browse(reqType, domain)
                .compose(mRxDnssd.resolve())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(servicesAction, errorAction);
    }

}

package com.druk.servicebrowser.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.druk.servicebrowser.BonjourApplication
import com.github.druk.rx2dnssd.BonjourService
import com.github.druk.rx2dnssd.Rx2Dnssd
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers

class ServiceBrowserViewModel(application: Application) : AndroidViewModel(application) {

    private var mRxDnssd: Rx2Dnssd = BonjourApplication.getRxDnssd(application)
    private var mDisposable: Disposable? = null

    override fun onCleared() {
        super.onCleared()
        mDisposable?.dispose()
    }

    fun startDiscovery(reqType: String,
                       domain: String,
                       servicesAction: Consumer<BonjourService>,
                       errorAction: Consumer<Throwable>) {
        mDisposable = mRxDnssd.browse(reqType, domain)
            .compose(mRxDnssd.resolve())
            .compose(mRxDnssd.queryIPRecords())
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(servicesAction, errorAction)
    }

}
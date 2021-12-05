package com.druk.servicebrowser.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.druk.servicebrowser.BonjourApplication
import com.github.druk.rx2dnssd.BonjourService
import com.github.druk.rx2dnssd.Rx2Dnssd
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import io.reactivex.ObservableOnSubscribe
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import java.util.*

class ServiceDetailViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val HTTP_PROTOCOL = "http"
        private const val HTTPS_PROTOCOL = "https"
    }

    private var mRxDnssd: Rx2Dnssd = BonjourApplication.getRxDnssd(application)
    private var mResolveIPDisposable: Disposable? = null
    private var mResolveTXTDisposable: Disposable? = null
    private var mCheckHttpConnectionDisposable: Disposable? = null

    override fun onCleared() {
        super.onCleared()
        mResolveIPDisposable?.dispose()
        mResolveTXTDisposable?.dispose()
        mCheckHttpConnectionDisposable?.dispose()
    }

    fun resolveIPRecords(service: BonjourService, consumer: Consumer<BonjourService>) {
        mResolveIPDisposable = mRxDnssd.queryIPRecords(service)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ bonjourService: BonjourService ->
                if (bonjourService.isLost) {
                    return@subscribe
                }
                consumer.accept(bonjourService)
            }, this::reportError)
    }

    fun resolveTXTRecords(service: BonjourService, consumer: Consumer<BonjourService>) {
        mResolveTXTDisposable = mRxDnssd.queryTXTRecords(service)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ bonjourService: BonjourService ->
                if (bonjourService.isLost) {
                    return@subscribe
                }
                consumer.accept(bonjourService)
            }, this::reportError)
    }

    fun checkHttpConnection(service: BonjourService, consumer: Consumer<URL>?) {
        mCheckHttpConnectionDisposable?.dispose()
        mCheckHttpConnectionDisposable = checkService(service)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(consumer)
    }

    private fun reportError(throwable: Throwable) {
        Log.e("DNSSD", "Error: ", throwable)
    }

    private fun checkService(service: BonjourService): Observable<URL> {
        val urls = LinkedList<URL>()
        for (inetAddress in service.inetAddresses) {
            try {
                urls.add(URL(HTTP_PROTOCOL, inetAddress.hostAddress, service.port, ""))
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }
            try {
                urls.add(URL(HTTPS_PROTOCOL, inetAddress.hostAddress, service.port, ""))
            } catch (e: MalformedURLException) {
                e.printStackTrace()
            }
        }
        val observable = Observable.fromIterable(urls)
        return observable.flatMap(Function { url: URL ->
            Observable.create(
                ObservableOnSubscribe { observableEmitter: ObservableEmitter<URL> ->
                    val success = checkURL(url)
                    if (success) {
                        observableEmitter.onNext(url)
                        observableEmitter.onComplete()
                    }
                } as ObservableOnSubscribe<URL>)
        } as Function<URL, Observable<URL>>).take(1).subscribeOn(Schedulers.io())
    }

    private fun checkURL(url: URL): Boolean {
        try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return false
    }

}
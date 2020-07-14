package com.druk.servicebrowser.ui.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.druk.servicebrowser.BonjourApplication;
import com.github.druk.rx2dnssd.BonjourService;
import com.github.druk.rx2dnssd.Rx2Dnssd;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;

import io.reactivex.Observable;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;

public class ServiceDetailViewModel extends AndroidViewModel {

    private static final String HTTP_PROTOCOL = "http";
    private static final String HTTPS_PROTOCOL = "https";

    protected Rx2Dnssd mRxDnssd;

    private Disposable mResolveIPDisposable;
    private Disposable mResolveTXTDisposable;
    private Disposable mCheckHttpConnectionDisposable;

    public ServiceDetailViewModel(@NonNull Application application) {
        super(application);
        mRxDnssd = BonjourApplication.getRxDnssd(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mResolveIPDisposable != null) {
            mResolveIPDisposable.dispose();
        }
        if (mResolveTXTDisposable != null) {
            mResolveTXTDisposable.dispose();
        }
        if (mCheckHttpConnectionDisposable != null) {
            mCheckHttpConnectionDisposable.dispose();
        }
    }

    public void resolveIPRecords(BonjourService service, Consumer<BonjourService> consumer) {
        mResolveIPDisposable = mRxDnssd.queryIPRecords(service)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {
                    if (bonjourService.isLost()) {
                        return;
                    }
                    consumer.accept(bonjourService);
                }, throwable -> Log.e("DNSSD", "Error: ", throwable));
    }

    public void resolveTXTRecords(BonjourService service, Consumer<BonjourService> consumer) {
        mResolveTXTDisposable = mRxDnssd.queryTXTRecords(service)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bonjourService -> {
                    if (bonjourService.isLost()) {
                        return;
                    }
                    consumer.accept(bonjourService);
                }, throwable -> Log.e("DNSSD", "Error: ", throwable));
    }

    public void checkHttpConnection(BonjourService service, Consumer<URL> consumer) {
        if (mCheckHttpConnectionDisposable != null) {
            mCheckHttpConnectionDisposable.dispose();
        }
        mCheckHttpConnectionDisposable = checkService(service)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(consumer);
    }

    private Observable<URL> checkService(@NonNull BonjourService service) {
        LinkedList<URL> urls = new LinkedList<>();
        for (InetAddress inetAddress : service.getInetAddresses()) {
            try {
                urls.add(new URL(HTTP_PROTOCOL, inetAddress.getHostAddress(), service.getPort(), ""));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
            try {
                urls.add(new URL(HTTPS_PROTOCOL, inetAddress.getHostAddress(), service.getPort(), ""));
            } catch (MalformedURLException e) {
                e.printStackTrace();
            }
        }

        Observable<URL> observable = Observable.fromIterable(urls);
        return observable.flatMap((Function<URL, Observable<URL>>) url -> Observable.create((ObservableOnSubscribe<URL>) observableEmitter -> {
            boolean success = checkURL(url);
            if (success) {
                observableEmitter.onNext(url);
                observableEmitter.onComplete();
            }
        })).take(1).subscribeOn(Schedulers.io());
    }

    private boolean checkURL(@NonNull URL url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true;
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return false;
    }

}

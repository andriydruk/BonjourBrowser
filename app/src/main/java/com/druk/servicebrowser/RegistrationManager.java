package com.druk.servicebrowser;

import android.content.Context;

import com.github.druk.rx2dnssd.BonjourService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.subjects.PublishSubject;


public class RegistrationManager {

    private final Map<BonjourService, Disposable> mRegistrations = new HashMap<>();

    public Observable<BonjourService> register(Context context, BonjourService bonjourService) {
        PublishSubject<BonjourService> subject = PublishSubject.create();
        final Disposable[] subscriptions = new Disposable[1];
        subscriptions[0] = BonjourApplication.getRxDnssd(context).register(bonjourService)
                .doOnNext(service -> mRegistrations.put(service, subscriptions[0]))
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(subject::onNext);
        return subject;
    }

    public void unregister(BonjourService service) {
        Disposable subscription = mRegistrations.remove(service);
        subscription.dispose();
    }

    public List<BonjourService> getRegisteredServices() {
        return Collections.unmodifiableList(new ArrayList<>(mRegistrations.keySet()));
    }
}

package com.druk.servicebrowser;

import com.github.druk.rxdnssd.BonjourService;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

public class RegistrationManager {

    private final Map<BonjourService, Subscription> mRegistrations = new HashMap<>();

    public Observable<BonjourService> register(Context context, BonjourService bonjourService) {
        PublishSubject<BonjourService> subject = PublishSubject.create();
        final Subscription[] subscriptions = new Subscription[1];
        subscriptions[0] = BonjourApplication.getRxDnssd(context).register(bonjourService)
                .doOnNext(new Action1<BonjourService>() {
                    @Override
                    public void call(BonjourService service) {
                        mRegistrations.put(service, subscriptions[0]);
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(subject);
        return subject;
    }

    public void unregister(BonjourService service) {
        Subscription subscription = mRegistrations.remove(service);
        subscription.unsubscribe();
    }

    public List<BonjourService> getRegisteredServices() {
        return Collections.unmodifiableList(new ArrayList<>(mRegistrations.keySet()));
    }
}

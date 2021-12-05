package com.druk.servicebrowser

import android.content.Context
import com.druk.servicebrowser.BonjourApplication.Companion.getRxDnssd
import com.github.druk.rx2dnssd.BonjourService
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.subjects.PublishSubject
import java.util.*

class RegistrationManager {

    private val mRegistrations: MutableMap<BonjourService, Disposable> = HashMap()

    fun register(context: Context, bonjourService: BonjourService): Observable<BonjourService> {
        val subject = PublishSubject.create<BonjourService>()
        val subscriptions = arrayOfNulls<Disposable>(1)
        subscriptions[0] = getRxDnssd(context).register(bonjourService)
            .doOnNext { service: BonjourService -> mRegistrations[service] = subscriptions[0]!! }
            .subscribeOn(AndroidSchedulers.mainThread())
            .subscribe { t: BonjourService -> subject.onNext(t) }
        return subject
    }

    fun unregister(service: BonjourService) {
        val subscription = mRegistrations.remove(service)
        subscription?.dispose()
    }

    val registeredServices: List<BonjourService>
        get() = Collections.unmodifiableList(ArrayList(mRegistrations.keys))
}
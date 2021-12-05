package com.druk.servicebrowser.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.druk.servicebrowser.BonjourApplication
import com.druk.servicebrowser.Config
import com.github.druk.rx2dnssd.BonjourService
import com.github.druk.rx2dnssd.Rx2Dnssd
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import java.util.*

class RegTypeBrowserViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private fun createKey(domain: String?, regType: String, serviceName: String): String {
            return domain + regType + serviceName
        }
    }

    val services = HashMap<String, BonjourDomain>()

    private var mRxDnssd: Rx2Dnssd = BonjourApplication.getRxDnssd(application)
    private val mBrowsers = HashMap<String, Disposable>()
    private var mDisposable: Disposable? = null

    override fun onCleared() {
        super.onCleared()
        mDisposable?.dispose()
        services.clear()
        synchronized(this) {
            for (subscription in mBrowsers.values) {
                subscription.dispose()
            }
            mBrowsers.clear()
        }
    }

    fun startDiscovery(servicesAction: Consumer<Collection<BonjourDomain>>,
                       errorAction: Consumer<Throwable>) {
        val serviceAction = Consumer { service: BonjourService ->
            val regTypeParts = service.regType.split(Config.REG_TYPE_SEPARATOR).toTypedArray()
            val serviceRegType = regTypeParts[0]
            val protocolSuffix = regTypeParts[1]
            val key = createKey(Config.EMPTY_DOMAIN,
                protocolSuffix + "." + service.domain,
                serviceRegType
            )
            val domain = services[key]
            if (domain != null) {
                if (service.isLost) {
                    domain.serviceCount--
                } else {
                    domain.serviceCount++
                }
                servicesAction.accept(services.values)
            } else {
                Log.w("TAG", "Service from unknown service type $key")
            }
        }
        val reqTypeAction: Consumer<BonjourService> = object : Consumer<BonjourService> {
            override fun accept(service: BonjourService) {
                if (service.isLost) {
                    //Ignore this call
                    return
                }
                val regTypeParts = service.regType.split(Config.REG_TYPE_SEPARATOR).toTypedArray()
                val protocolSuffix = regTypeParts[0]
                val serviceDomain = regTypeParts[1]
                if (Config.TCP_REG_TYPE_SUFFIX == protocolSuffix || Config.UDP_REG_TYPE_SUFFIX == protocolSuffix) {
                    val key = service.serviceName + "." + protocolSuffix
                    synchronized(this) {
                        if (!mBrowsers.containsKey(key)) {
                            mBrowsers[key] = mRxDnssd.browse(key, serviceDomain)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(serviceAction, errorAction)
                        }
                        services.put(
                            createKey(
                                service.domain,
                                service.regType,
                                service.serviceName
                            ), BonjourDomain(service)
                        )
                    }
                } else {
                    Log.e("TAG", "Unknown service protocol $protocolSuffix")
                    //Just ignore service with different protocol suffixes
                }
            }
        }
        mDisposable = mRxDnssd.browse(Config.SERVICES_DOMAIN, Config.LOCAL_DOMAIN)
            .subscribeOn(Schedulers.io())
            .subscribe(reqTypeAction, errorAction)
    }

    class BonjourDomain(bonjourService: BonjourService) : BonjourService(Builder(bonjourService)) {
        @JvmField
        var serviceCount = 0
    }

}
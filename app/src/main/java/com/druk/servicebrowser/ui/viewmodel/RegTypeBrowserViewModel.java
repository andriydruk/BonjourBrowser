package com.druk.servicebrowser.ui.viewmodel;

import static com.druk.servicebrowser.Config.EMPTY_DOMAIN;
import static com.druk.servicebrowser.Config.TCP_REG_TYPE_SUFFIX;
import static com.druk.servicebrowser.Config.UDP_REG_TYPE_SUFFIX;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.Config;
import com.github.druk.rx2dnssd.BonjourService;
import com.github.druk.rx2dnssd.Rx2Dnssd;

import java.util.Collection;
import java.util.HashMap;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;

public class RegTypeBrowserViewModel extends AndroidViewModel {

    private final HashMap<String, Disposable> mBrowsers = new HashMap<>();
    private final HashMap<String, BonjourDomain> mServices = new HashMap<>();

    protected Rx2Dnssd mRxDnssd;
    protected Disposable mDisposable;

    public RegTypeBrowserViewModel(@NonNull Application application) {
        super(application);
        mRxDnssd = BonjourApplication.getRxDnssd(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (mDisposable != null) {
            mDisposable.dispose();
        }
        mServices.clear();
        synchronized (this) {
            for (Disposable subscription : mBrowsers.values()) {
                subscription.dispose();
            }
            mBrowsers.clear();
        }
    }

    public HashMap<String, BonjourDomain> getServices() {
        return mServices;
    }

    public void startDiscovery(Consumer<Collection<BonjourDomain>> servicesAction, Consumer<Throwable> errorAction) {
        final Consumer<BonjourService> serviceAction = service -> {
            String[] regTypeParts = service.getRegType().split(Config.REG_TYPE_SEPARATOR);
            String serviceRegType = regTypeParts[0];
            String protocolSuffix = regTypeParts[1];
            String key = RegTypeBrowserViewModel.createKey(EMPTY_DOMAIN, protocolSuffix + "." + service.getDomain(), serviceRegType);
            RegTypeBrowserViewModel.BonjourDomain domain = mServices.get(key);
            if (domain != null) {
                if (service.isLost()) {
                    domain.serviceCount--;
                } else {
                    domain.serviceCount++;
                }
                servicesAction.accept(mServices.values());
            } else {
                Log.w("TAG", "Service from unknown service type " + key);
            }
        };

        Consumer<BonjourService> reqTypeAction = new Consumer<BonjourService>() {
            @Override
            public void accept(BonjourService service) {
                if (service.isLost()) {
                    //Ignore this call
                    return;
                }
                String[] regTypeParts = service.getRegType().split(Config.REG_TYPE_SEPARATOR);
                String protocolSuffix = regTypeParts[0];
                String serviceDomain = regTypeParts[1];
                if (TCP_REG_TYPE_SUFFIX.equals(protocolSuffix) || UDP_REG_TYPE_SUFFIX.equals(protocolSuffix)) {
                    String key = service.getServiceName() + "." + protocolSuffix;
                    synchronized (this) {
                        if (!mBrowsers.containsKey(key)) {
                            mBrowsers.put(key, mRxDnssd.browse(key, serviceDomain)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(serviceAction, errorAction));
                        }
                        mServices.put(createKey(service.getDomain(), service.getRegType(), service.getServiceName()), new BonjourDomain(service));
                    }
                } else {
                    Log.e("TAG", "Unknown service protocol " + protocolSuffix);
                    //Just ignore service with different protocol suffixes
                }
            }
        };

        mDisposable = mRxDnssd.browse(Config.SERVICES_DOMAIN, Config.LOCAL_DOMAIN)
                .subscribeOn(Schedulers.io())
                .subscribe(reqTypeAction, errorAction);
    }

    private static String createKey(String domain, String regType, String serviceName) {
        return domain + regType + serviceName;
    }

    public static class BonjourDomain extends BonjourService {
        public int serviceCount = 0;

        public BonjourDomain(BonjourService bonjourService){
            super(new BonjourService.Builder(bonjourService));
        }
    }

}

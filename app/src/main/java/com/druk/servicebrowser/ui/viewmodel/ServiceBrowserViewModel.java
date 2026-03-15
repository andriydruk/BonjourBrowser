package com.druk.servicebrowser.ui.viewmodel;

import android.app.Application;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.BonjourServiceInfo;

public class ServiceBrowserViewModel extends AndroidViewModel {

    private static final String TAG = "ServiceBrowserVM";

    private final NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private final MutableLiveData<BonjourServiceInfo> serviceEvent = new MutableLiveData<>();
    private final MutableLiveData<Throwable> errorEvent = new MutableLiveData<>();

    public ServiceBrowserViewModel(@NonNull Application application) {
        super(application);
        nsdManager = BonjourApplication.getNsdManager(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopDiscovery();
    }

    public LiveData<BonjourServiceInfo> getServiceEvent() {
        return serviceEvent;
    }

    public LiveData<Throwable> getErrorEvent() {
        return errorEvent;
    }

    public void startDiscovery(String serviceType, String domain) {
        stopDiscovery();

        // NsdManager doesn't use trailing dots
        String type = serviceType;
        if (type != null && type.endsWith(".")) {
            type = type.substring(0, type.length() - 1);
        }

        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Discovery started: " + regType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
                nsdManager.resolveService(nsdServiceInfo, new NsdManager.ResolveListener() {
                    @Override
                    public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        Log.w(TAG, "Resolve failed for " + serviceInfo.getServiceName() + ": " + errorCode);
                    }

                    @Override
                    public void onServiceResolved(NsdServiceInfo serviceInfo) {
                        BonjourServiceInfo info = BonjourServiceInfo.fromNsdServiceInfo(serviceInfo, false);
                        serviceEvent.postValue(info);
                    }
                });
            }

            @Override
            public void onServiceLost(NsdServiceInfo nsdServiceInfo) {
                BonjourServiceInfo info = new BonjourServiceInfo.Builder()
                        .serviceName(nsdServiceInfo.getServiceName())
                        .regType(nsdServiceInfo.getServiceType())
                        .lost(true)
                        .build();
                serviceEvent.postValue(info);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.d(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Start discovery failed: " + serviceType + " error: " + errorCode);
                errorEvent.postValue(new RuntimeException("Discovery failed for " + serviceType + " (error " + errorCode + ")"));
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop discovery failed: " + serviceType + " error: " + errorCode);
            }
        };

        nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    private void stopDiscovery() {
        if (discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Discovery already stopped", e);
            }
            discoveryListener = null;
        }
    }
}

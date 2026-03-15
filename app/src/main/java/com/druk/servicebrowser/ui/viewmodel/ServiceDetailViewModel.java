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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ServiceDetailViewModel extends AndroidViewModel {

    private static final String TAG = "ServiceDetailVM";
    private static final String HTTP_PROTOCOL = "http";
    private static final String HTTPS_PROTOCOL = "https";

    private final NsdManager nsdManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private NsdManager.ServiceInfoCallback serviceInfoCallback;

    private final MutableLiveData<BonjourServiceInfo> serviceInfoLiveData = new MutableLiveData<>();
    private final MutableLiveData<URL> httpUrlLiveData = new MutableLiveData<>();

    public ServiceDetailViewModel(@NonNull Application application) {
        super(application);
        nsdManager = BonjourApplication.getNsdManager(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (serviceInfoCallback != null) {
            try {
                nsdManager.unregisterServiceInfoCallback(serviceInfoCallback);
            } catch (IllegalArgumentException ignored) {
            }
            serviceInfoCallback = null;
        }
        executor.shutdownNow();
    }

    public LiveData<BonjourServiceInfo> getServiceInfoLiveData() {
        return serviceInfoLiveData;
    }

    public LiveData<URL> getHttpUrlLiveData() {
        return httpUrlLiveData;
    }

    public void resolve(BonjourServiceInfo service) {
        NsdServiceInfo nsdServiceInfo = new NsdServiceInfo();
        nsdServiceInfo.setServiceName(service.getServiceName());
        nsdServiceInfo.setServiceType(service.getRegType());

        serviceInfoCallback = new NsdManager.ServiceInfoCallback() {
            @Override
            public void onServiceInfoCallbackRegistrationFailed(int errorCode) {
                Log.e(TAG, "ServiceInfoCallback registration failed: " + errorCode);
            }

            @Override
            public void onServiceUpdated(@NonNull NsdServiceInfo nsdServiceInfo) {
                BonjourServiceInfo info = BonjourServiceInfo.fromNsdServiceInfo(nsdServiceInfo, false);
                serviceInfoLiveData.postValue(info);
                checkHttpConnection(info);
            }

            @Override
            public void onServiceLost() {
                Log.d(TAG, "Service lost");
            }

            @Override
            public void onServiceInfoCallbackUnregistered() {
                Log.d(TAG, "ServiceInfoCallback unregistered");
            }
        };

        nsdManager.registerServiceInfoCallback(nsdServiceInfo, Executors.newSingleThreadExecutor(), serviceInfoCallback);
    }

    private void checkHttpConnection(BonjourServiceInfo service) {
        executor.execute(() -> {
            LinkedList<URL> urls = new LinkedList<>();
            for (InetAddress inetAddress : service.getInetAddresses()) {
                try {
                    urls.add(new URL(HTTP_PROTOCOL, inetAddress.getHostAddress(), service.getPort(), ""));
                } catch (MalformedURLException ignored) {
                }
                try {
                    urls.add(new URL(HTTPS_PROTOCOL, inetAddress.getHostAddress(), service.getPort(), ""));
                } catch (MalformedURLException ignored) {
                }
            }

            for (URL url : urls) {
                if (checkURL(url)) {
                    httpUrlLiveData.postValue(url);
                    return;
                }
            }
        });
    }

    private boolean checkURL(@NonNull URL url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3000);
            connection.connect();
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true;
            }
        } catch (IOException ignored) {
        }
        return false;
    }
}

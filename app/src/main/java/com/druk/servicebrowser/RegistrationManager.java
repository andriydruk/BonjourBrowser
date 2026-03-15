package com.druk.servicebrowser;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegistrationManager {

    private static final String TAG = "RegistrationManager";

    private final NsdManager nsdManager;
    private final Map<BonjourServiceInfo, NsdManager.RegistrationListener> mRegistrations = new HashMap<>();

    public RegistrationManager(Context context) {
        nsdManager = BonjourApplication.getNsdManager(context);
    }

    public void register(BonjourServiceInfo bonjourServiceInfo, RegistrationCallback callback) {
        NsdServiceInfo nsdServiceInfo = new NsdServiceInfo();
        nsdServiceInfo.setServiceName(bonjourServiceInfo.getServiceName());
        nsdServiceInfo.setServiceType(bonjourServiceInfo.getRegType());
        nsdServiceInfo.setPort(bonjourServiceInfo.getPort());

        for (Map.Entry<String, String> entry : bonjourServiceInfo.getTxtRecords().entrySet()) {
            nsdServiceInfo.setAttribute(entry.getKey(), entry.getValue());
        }

        NsdManager.RegistrationListener listener = new NsdManager.RegistrationListener() {
            @Override
            public void onServiceRegistered(NsdServiceInfo serviceInfo) {
                BonjourServiceInfo registered = new BonjourServiceInfo.Builder()
                        .serviceName(serviceInfo.getServiceName())
                        .regType(bonjourServiceInfo.getRegType())
                        .port(bonjourServiceInfo.getPort())
                        .txtRecords(bonjourServiceInfo.getTxtRecords())
                        .build();
                mRegistrations.put(registered, this);
                callback.onServiceRegistered(registered);
            }

            @Override
            public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Registration failed: " + errorCode);
                callback.onRegistrationFailed(errorCode);
            }

            @Override
            public void onServiceUnregistered(NsdServiceInfo serviceInfo) {
                Log.i(TAG, "Service unregistered: " + serviceInfo.getServiceName());
            }

            @Override
            public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Unregistration failed: " + errorCode);
            }
        };

        nsdManager.registerService(nsdServiceInfo, NsdManager.PROTOCOL_DNS_SD, listener);
    }

    public void unregister(BonjourServiceInfo service) {
        NsdManager.RegistrationListener listener = mRegistrations.remove(service);
        if (listener != null) {
            nsdManager.unregisterService(listener);
        }
    }

    public List<BonjourServiceInfo> getRegisteredServices() {
        return Collections.unmodifiableList(new ArrayList<>(mRegistrations.keySet()));
    }

    public interface RegistrationCallback {
        void onServiceRegistered(BonjourServiceInfo service);
        void onRegistrationFailed(int errorCode);
    }
}

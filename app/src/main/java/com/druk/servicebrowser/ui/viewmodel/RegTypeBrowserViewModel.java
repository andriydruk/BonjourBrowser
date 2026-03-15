package com.druk.servicebrowser.ui.viewmodel;

import android.app.Application;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.druk.servicebrowser.BonjourApplication;
import com.druk.servicebrowser.BonjourServiceInfo;
import com.druk.servicebrowser.Config;
import com.druk.servicebrowser.ServiceTypeResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Discovers available mDNS service types on the local network.
 * <p>
 * NsdManager does not support the _services._dns-sd._udp meta-query, so we perform
 * it via a persistent mDNS multicast listener ({@link ServiceTypeResolver}). Each new
 * service type is immediately handed to NsdManager for per-service browsing.
 */
public class RegTypeBrowserViewModel extends AndroidViewModel {

    private static final String TAG = "RegTypeBrowserVM";

    /** Delay before retrying types that failed with FAILURE_MAX_LIMIT. */
    private static final long RETRY_DELAY_MS = 3000;

    private final NsdManager nsdManager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ServiceTypeResolver resolver = new ServiceTypeResolver();

    /** Active NsdManager discovery listeners keyed by service type. */
    private final HashMap<String, NsdManager.DiscoveryListener> activeListeners = new HashMap<>();

    /** Types that found at least one service. Thread-safe. */
    private final ConcurrentHashMap<String, BonjourDomain> foundTypes = new ConcurrentHashMap<>();

    /** Queue of types that hit FAILURE_MAX_LIMIT and need retry. */
    private final Queue<String> retryQueue = new LinkedList<>();

    private final MutableLiveData<Collection<BonjourDomain>> servicesLiveData = new MutableLiveData<>();
    private final MutableLiveData<Throwable> errorLiveData = new MutableLiveData<>();

    private final Runnable retryRunnable = () -> {
        synchronized (RegTypeBrowserViewModel.this) {
            drainQueue(retryQueue);
        }
    };

    public RegTypeBrowserViewModel(@NonNull Application application) {
        super(application);
        nsdManager = BonjourApplication.getNsdManager(application);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        resolver.stop();
        handler.removeCallbacksAndMessages(null);
        executor.shutdownNow();
        synchronized (this) {
            for (NsdManager.DiscoveryListener listener : activeListeners.values()) {
                try {
                    nsdManager.stopServiceDiscovery(listener);
                } catch (IllegalArgumentException ignored) {
                }
            }
            activeListeners.clear();
        }
        foundTypes.clear();
    }

    public ConcurrentHashMap<String, BonjourDomain> getServices() {
        return foundTypes;
    }

    public LiveData<Collection<BonjourDomain>> getServicesLiveData() {
        return servicesLiveData;
    }

    public LiveData<Throwable> getErrorLiveData() {
        return errorLiveData;
    }

    public void startDiscovery() {
        // Run the mDNS listener on a background thread — it stays open until onCleared
        executor.execute(() -> resolver.start(serviceType -> {
            // Called on the background thread for each newly discovered type — start browsing immediately
            handler.post(() -> {
                synchronized (RegTypeBrowserViewModel.this) {
                    if (!activeListeners.containsKey(serviceType)) {
                        startSingleDiscovery(serviceType);
                    }
                }
            });
        }));
    }

    /** Try to start all queued types; stop on first FAILURE_MAX_LIMIT. */
    private void drainQueue(Queue<String> queue) {
        while (!queue.isEmpty()) {
            String type = queue.poll();
            if (type == null || activeListeners.containsKey(type)) continue;
            if (!startSingleDiscovery(type)) {
                queue.add(type); // put it back
                handler.postDelayed(retryRunnable, RETRY_DELAY_MS);
                break;
            }
        }
    }

    /**
     * @return true if started successfully, false if hit system limit
     */
    private synchronized boolean startSingleDiscovery(String serviceType) {
        NsdManager.DiscoveryListener listener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Browsing: " + regType);
            }

            @Override
            public void onServiceFound(NsdServiceInfo nsdServiceInfo) {
                handleServiceEvent(serviceType, false);
            }

            @Override
            public void onServiceLost(NsdServiceInfo nsdServiceInfo) {
                handleServiceEvent(serviceType, true);
            }

            @Override
            public void onDiscoveryStopped(String regType) {
            }

            @Override
            public void onStartDiscoveryFailed(String regType, int errorCode) {
                Log.w(TAG, "Browse failed for " + regType + ": " + errorCode);
                synchronized (RegTypeBrowserViewModel.this) {
                    activeListeners.remove(serviceType);
                }
                if (errorCode == NsdManager.FAILURE_MAX_LIMIT) {
                    synchronized (RegTypeBrowserViewModel.this) {
                        retryQueue.add(serviceType);
                    }
                    handler.removeCallbacks(retryRunnable);
                    handler.postDelayed(retryRunnable, RETRY_DELAY_MS);
                }
            }

            @Override
            public void onStopDiscoveryFailed(String regType, int errorCode) {
                Log.w(TAG, "Stop failed for " + regType + ": " + errorCode);
            }
        };

        activeListeners.put(serviceType, listener);

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
            return true;
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Failed to start browse for " + serviceType, e);
            activeListeners.remove(serviceType);
            return false;
        }
    }

    private void handleServiceEvent(String serviceType, boolean lost) {
        String[] parts = serviceType.split("\\.");
        if (parts.length < 2) return;

        String serviceName = parts[0];
        String protocolSuffix = parts[1];

        BonjourDomain domain = foundTypes.get(serviceType);
        if (domain == null) {
            BonjourServiceInfo info = new BonjourServiceInfo.Builder()
                    .serviceName(serviceName)
                    .regType(protocolSuffix + "." + Config.LOCAL_DOMAIN)
                    .domain(Config.EMPTY_DOMAIN)
                    .build();
            domain = new BonjourDomain(info);
            foundTypes.put(serviceType, domain);
        }

        if (lost) {
            domain.serviceCount = Math.max(0, domain.serviceCount - 1);
        } else {
            domain.serviceCount++;
        }

        servicesLiveData.postValue(new ArrayList<>(foundTypes.values()));
    }

    public static class BonjourDomain extends BonjourServiceInfo {
        public int serviceCount = 0;

        public BonjourDomain(BonjourServiceInfo info) {
            super(new Builder(info));
        }
    }
}

package com.druk.servicebrowser;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegTypeManager {

    private static final String TAG = "RegTypeManager";

    private volatile TreeMap<String, String> mServiceNamesTree;
    private final Context mContext;

    RegTypeManager(@NonNull Context context) {
        this.mContext = context;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> getRegTypeDescription("_zigbee-gateway._udp."));
        executor.shutdown();
    }

    public List<String> getListRegTypes() {
        if (this.mServiceNamesTree == null) {
            return new LinkedList<>();
        }
        return new LinkedList<>(mServiceNamesTree.keySet());
    }

    public String getRegTypeDescription(String regType) {
        if (mServiceNamesTree == null) {
            synchronized (this) {
                if (mServiceNamesTree == null) {
                    TreeMap<String, String> tree = new TreeMap<>();
                    try {
                        InputStream is = mContext.getAssets().open("service-names-port-numbers.csv");
                        try {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                            String line;
                            while ((line = reader.readLine()) != null) {
                                String[] rowData = line.split(",");
                                if (rowData.length < 4 || TextUtils.isEmpty(rowData[0]) || TextUtils.isEmpty(rowData[2]) || TextUtils.isEmpty(rowData[3])) {
                                    continue;
                                }
                                if (rowData[0].contains(" ") || rowData[2].contains(" ")) {
                                    continue;
                                }
                                tree.put("_" + rowData[0] + "._" + rowData[2] + ".", rowData[3]);
                            }
                        } catch (IOException ex) {
                            // handle exception
                        } finally {
                            try {
                                is.close();
                            } catch (IOException e) {
                                Log.e(TAG, "init error: ", e);
                            }
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "service-names-port-numbers.csv reading error: ", e);
                    }
                    mServiceNamesTree = tree;
                }
            }
        }
        return mServiceNamesTree.get(regType);
    }
}

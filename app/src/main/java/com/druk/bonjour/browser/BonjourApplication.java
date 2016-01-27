/*
 * Copyright (C) 2015 Andriy Druk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.druk.bonjour.browser;

import com.github.druk.RxDnssd;

import android.app.Application;
import android.content.Context;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.TreeMap;

public class BonjourApplication extends Application {

    private static final String TAG = "BonjourApplication";
    private TreeMap<String, String> mServiceNamesTree;

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {

            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        RxDnssd.init(this);
    }

    public static String getRegTypeDescription(@NonNull Context context, String regType) {
        return ((BonjourApplication) context.getApplicationContext()).getRegTypeDescription(regType);
    }

    private String getRegTypeDescription(String regType) {
        if (mServiceNamesTree == null){
            mServiceNamesTree = new TreeMap<>();
            try {
                InputStream is = getAssets().open("service-names-port-numbers.csv");
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] rowData = line.split(",");
                        if (rowData.length < 4 || TextUtils.isEmpty(rowData[0]) || TextUtils.isEmpty(rowData[2]) || TextUtils.isEmpty(rowData[3])) {
                            continue;
                        }
                        mServiceNamesTree.put("_" + rowData[0] + "._" + rowData[2] + ".", rowData[3]);
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
                e.printStackTrace();
                Log.e(TAG, "service-names-port-numbers.csv reading error: ", e);
            }
        }
        return mServiceNamesTree.get(regType);
    }
}

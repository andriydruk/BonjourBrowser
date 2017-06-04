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
package com.druk.servicebrowser;

import com.github.druk.rxdnssd.RxDnssd;
import com.github.druk.rxdnssd.RxDnssdBindable;
import com.github.druk.rxdnssd.RxDnssdEmbedded;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.StrictMode;
import android.support.annotation.NonNull;
import android.util.Log;

public class BonjourApplication extends Application {

    private static final String TAG = "BonjourApplication";
    private RxDnssd mRxDnssd;
    private RegistrationManager mRegistrationManager;
    private RegTypeManager mRegTypeManager;

    @Override
    public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {

            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()   // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build());
        }
        mRxDnssd = createDnssd();
        mRegistrationManager = new RegistrationManager();
        mRegTypeManager = new RegTypeManager(this);
    }

    public static RxDnssd getRxDnssd(@NonNull Context context){
        return ((BonjourApplication)context.getApplicationContext()).mRxDnssd;
    }

    public static RegistrationManager getRegistrationManager(@NonNull Context context){
        return ((BonjourApplication) context.getApplicationContext()).mRegistrationManager;
    }

    public static RegTypeManager getRegTypeManager(@NonNull Context context){
        return ((BonjourApplication) context.getApplicationContext()).mRegTypeManager;
    }

    private RxDnssd createDnssd(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN){
            Log.i(TAG, "Using embedded version of dns sd because of API < 16");
            return new RxDnssdEmbedded();
        }
        if (Build.VERSION.RELEASE.contains("4.4.2") && Build.MANUFACTURER.toLowerCase().contains("samsung")){
            Log.i(TAG, "Using embedded version of dns sd because of Samsung 4.4.2");
            return new RxDnssdEmbedded();
        }
        Log.i(TAG, "Using systems dns sd daemon");
        return new RxDnssdBindable(this);
    }
}

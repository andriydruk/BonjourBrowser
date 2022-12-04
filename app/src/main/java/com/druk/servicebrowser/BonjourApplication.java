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

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.StrictMode;
import android.util.Log;

import androidx.annotation.NonNull;

import com.github.druk.rx2dnssd.Rx2Dnssd;
import com.github.druk.rx2dnssd.Rx2DnssdBindable;
import com.github.druk.rx2dnssd.Rx2DnssdEmbedded;

public class BonjourApplication extends Application {

    private static final String TAG = "BonjourApplication";
    private Rx2Dnssd mRxDnssd;
    private RegistrationManager mRegistrationManager;
    private RegTypeManager mRegTypeManager;
    private FavouritesManager mFavouritesManager;

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
        mFavouritesManager = new FavouritesManager(this);
    }

    public static BonjourApplication getApplication(@NonNull Context context){
        return ((BonjourApplication)context.getApplicationContext());
    }

    public static Rx2Dnssd getRxDnssd(@NonNull Context context){
        return ((BonjourApplication)context.getApplicationContext()).mRxDnssd;
    }

    public static RegistrationManager getRegistrationManager(@NonNull Context context){
        return ((BonjourApplication) context.getApplicationContext()).mRegistrationManager;
    }

    public static RegTypeManager getRegTypeManager(@NonNull Context context){
        return ((BonjourApplication) context.getApplicationContext()).mRegTypeManager;
    }

    public static FavouritesManager getFavouritesManager(@NonNull Context context){
        return ((BonjourApplication)context.getApplicationContext()).mFavouritesManager;
    }

    private Rx2Dnssd createDnssd() {
        // https://developer.android.com/about/versions/12/behavior-changes-12#mdnsresponder
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.i(TAG, "Using embedded version of dns sd");
            return new Rx2DnssdEmbedded(this);
        }
        else {
            Log.i(TAG, "Using bindable version of dns sd");
            return new Rx2DnssdBindable(this);
        }
    }
}

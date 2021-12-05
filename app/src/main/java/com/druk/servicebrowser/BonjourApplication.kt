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
package com.druk.servicebrowser

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import com.github.druk.rx2dnssd.Rx2Dnssd
import com.github.druk.rx2dnssd.Rx2DnssdEmbedded

class BonjourApplication : Application() {

    private lateinit var mRxDnssd: Rx2Dnssd
    private lateinit var mRegistrationManager: RegistrationManager
    private lateinit var mRegTypeManager: RegTypeManager
    private lateinit  var mFavouritesManager: FavouritesManager

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork() // or .detectAll() for all detectable problems
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
        mRxDnssd = createDnssd()
        mRegistrationManager = RegistrationManager()
        mRegTypeManager = RegTypeManager(this)
        mFavouritesManager = FavouritesManager(this)
    }

    private fun createDnssd(): Rx2Dnssd {
        Log.i(TAG, "Using bindable version of dns sd")
        return Rx2DnssdEmbedded(this)
    }

    companion object {

        private const val TAG = "BonjourApplication"

        fun getApplication(context: Context): BonjourApplication {
            return context.applicationContext as BonjourApplication
        }

        @JvmStatic
        fun getRxDnssd(context: Context): Rx2Dnssd {
            return (context.applicationContext as BonjourApplication).mRxDnssd
        }

        @JvmStatic
        fun getRegistrationManager(context: Context): RegistrationManager {
            return (context.applicationContext as BonjourApplication).mRegistrationManager
        }

        @JvmStatic
        fun getRegTypeManager(context: Context): RegTypeManager {
            return (context.applicationContext as BonjourApplication).mRegTypeManager
        }

        fun getFavouritesManager(context: Context): FavouritesManager {
            return (context.applicationContext as BonjourApplication).mFavouritesManager
        }
    }
}
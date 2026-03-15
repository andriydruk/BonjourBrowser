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
import android.net.nsd.NsdManager
import android.os.StrictMode

class BonjourApplication : Application() {

    lateinit var registrationManager: RegistrationManager
        private set
    lateinit var regTypeManager: RegTypeManager
        private set
    lateinit var favouritesManager: FavouritesManager
        private set

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
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
        registrationManager = RegistrationManager(this)
        regTypeManager = RegTypeManager(this)
        favouritesManager = FavouritesManager(this)
    }

    companion object {
        @JvmStatic
        fun getApplication(context: Context): BonjourApplication =
            context.applicationContext as BonjourApplication

        @JvmStatic
        fun getNsdManager(context: Context): NsdManager =
            context.getSystemService(NsdManager::class.java)

        @JvmStatic
        fun getRegistrationManager(context: Context): RegistrationManager =
            getApplication(context).registrationManager

        @JvmStatic
        fun getRegTypeManager(context: Context): RegTypeManager =
            getApplication(context).regTypeManager

        @JvmStatic
        fun getFavouritesManager(context: Context): FavouritesManager =
            getApplication(context).favouritesManager
    }
}

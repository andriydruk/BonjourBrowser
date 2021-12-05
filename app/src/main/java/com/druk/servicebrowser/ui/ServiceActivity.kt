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
package com.druk.servicebrowser.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.TextView
import android.widget.Toast
import com.druk.servicebrowser.ui.fragment.ServiceDetailFragment.Companion.newInstance
import androidx.appcompat.app.AppCompatActivity
import com.druk.servicebrowser.ui.fragment.ServiceDetailFragment.ServiceDetailListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.druk.servicebrowser.ui.fragment.ServiceDetailFragment
import com.github.druk.rx2dnssd.BonjourService
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.druk.servicebrowser.R
import com.druk.servicebrowser.Utils
import java.net.URL

class ServiceActivity : AppCompatActivity(), ServiceDetailListener {

    private lateinit var mServiceName: TextView
    private lateinit var mRegType: TextView
    private lateinit var mDomain: TextView
    private lateinit var mLastTimestamp: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        mServiceName = findViewById(R.id.service_name)
        mRegType = findViewById(R.id.reg_type)
        mDomain = findViewById(R.id.domain)
        mLastTimestamp = findViewById(R.id.last_timestamp)
        val serviceDetailFragment: ServiceDetailFragment?
        val isRegistered = intent.getBooleanExtra(REGISTERED, false)
        if (savedInstanceState == null) {
            val service: BonjourService = intent.getParcelableExtra(SERVICE) ?: return
            serviceDetailFragment = newInstance(service)
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, serviceDetailFragment)
                .commit()
        } else {
            serviceDetailFragment =
                supportFragmentManager.findFragmentById(R.id.content) as ServiceDetailFragment?
        }
        if (isRegistered && fab != null) {
            fab.visibility = View.VISIBLE
            fab.setOnClickListener(serviceDetailFragment)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onServiceUpdated(service: BonjourService) {
        mServiceName.text = service.serviceName
        mDomain.text = getString(R.string.domain, service.domain)
        mRegType.text = getString(R.string.reg_type, service.regType)
        mLastTimestamp.text = getString(
            R.string.last_update,
            Utils.formatTime(System.currentTimeMillis())
        )
    }

    override fun onServiceStopped(service: BonjourService) {
        setResult(RESULT_OK, Intent().putExtra(SERVICE, service))
        finish()
    }

    override fun onHttpServerFound(url: URL) {
        val fab = findViewById<FloatingActionButton>(R.id.fab)
        if (fab.visibility != View.VISIBLE) {
            fab.visibility = View.VISIBLE
            fab.scaleX = 0f
            fab.scaleY = 0f
            fab.animate()
                .alpha(1.0f)
                .scaleX(1f)
                .scaleY(1f)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
        fab.setOnClickListener {
            val builder = CustomTabsIntent.Builder()
            builder.setToolbarColor(ContextCompat.getColor(this, R.color.primary))
            val customTabsIntent = builder.build()
            try {
                customTabsIntent.launchUrl(this, Uri.parse(url.toString()))
            } catch (e: Throwable) {
                Toast.makeText(this, "Can't find browser", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val SERVICE = "mService"
        private const val REGISTERED = "registered"
        fun startActivity(context: Context, service: BonjourService?) {
            context.startActivity(
                Intent(context, ServiceActivity::class.java).putExtra(
                    SERVICE,
                    service
                )
            )
        }

        @JvmStatic
        fun startActivity(context: Context, service: BonjourService, isRegistered: Boolean): Intent {
            return Intent(context, ServiceActivity::class.java).putExtra(SERVICE, service).putExtra(
                REGISTERED, isRegistered
            )
        }

        @JvmStatic
        fun parseResult(intent: Intent): BonjourService? {
            return intent.getParcelableExtra(SERVICE)
        }
    }
}
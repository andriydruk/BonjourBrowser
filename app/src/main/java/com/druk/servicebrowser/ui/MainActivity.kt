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

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import com.druk.servicebrowser.BonjourServiceInfo
import com.druk.servicebrowser.Config
import com.druk.servicebrowser.R
import com.druk.servicebrowser.Utils
import com.druk.servicebrowser.ui.fragment.RegTypeBrowserFragment
import com.druk.servicebrowser.ui.fragment.ServiceBrowserFragment
import com.druk.servicebrowser.ui.fragment.ServiceDetailFragment
import java.net.URL

class MainActivity : AppCompatActivity(),
    ServiceBrowserFragment.ServiceListener,
    ServiceDetailFragment.ServiceDetailListener {

    private var slidingPanelLayout: SlidingPaneLayout? = null
    private var noServiceTextView: TextView? = null
    private var serviceNameTextView: TextView? = null
    private var lastUpdatedTextView: TextView? = null

    private var domain: String? = null
    private var regType: String? = null
    private var serviceName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        slidingPanelLayout = findViewById(R.id.sliding_panel_layout)

        if (slidingPanelLayout != null) {
            slidingPanelLayout!!.openPane()
            noServiceTextView = findViewById(R.id.no_service)
            serviceNameTextView = findViewById(R.id.service_name)
            lastUpdatedTextView = findViewById(R.id.last_updated)
        }

        if (savedInstanceState == null) {
            domain = Config.LOCAL_DOMAIN
            supportFragmentManager.beginTransaction()
                .replace(R.id.first_panel, RegTypeBrowserFragment.newInstance(Config.TCP_REG_TYPE_SUFFIX))
                .commit()
        } else {
            domain = savedInstanceState.getString(PARAM_DOMAIN)
            regType = savedInstanceState.getString(PARAM_REG_TYPE)
            serviceName = savedInstanceState.getString(PARAM_SERVICE_NAME)
        }

        updateNavigation()
    }

    private fun updateNavigation() {
        title = domain + (regType?.let { "   >   $it" + (serviceName?.let { sn -> "   >   $sn" } ?: "") } ?: "")
        if (slidingPanelLayout != null) {
            noServiceTextView!!.visibility = if (serviceName == null) View.VISIBLE else View.GONE
            serviceNameTextView!!.visibility = if (serviceName == null) View.GONE else View.VISIBLE
            serviceNameTextView!!.text = serviceName
            lastUpdatedTextView!!.visibility = if (serviceName == null) View.GONE else View.VISIBLE
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_bonjour_browser, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_license -> {
                startActivity(Intent(this, LicensesActivity::class.java))
                true
            }
            R.id.action_register -> {
                RegistrationsActivity.startActivity(this)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onServiceWasSelected(domain: String, regType: String, service: BonjourServiceInfo) {
        if (domain == Config.EMPTY_DOMAIN) {
            val regTypeParts = service.regType!!.split(Config.REG_TYPE_SEPARATOR.toRegex())
            val serviceRegType = "${service.displayName}.${regTypeParts[0]}."
            val serviceDomain = "${regTypeParts[1]}."

            if (slidingPanelLayout != null) {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.second_panel, ServiceBrowserFragment.newInstance(serviceDomain, serviceRegType))
                    .commit()
                supportFragmentManager.findFragmentById(R.id.third_panel)?.let { fragment ->
                    supportFragmentManager.beginTransaction().remove(fragment).commit()
                }
                this.regType = serviceRegType
                this.serviceName = null
                updateNavigation()
            } else {
                startActivity(RegTypeActivity.createIntent(this, serviceRegType, serviceDomain))
            }
        } else {
            val fragment = ServiceDetailFragment.newInstance(service)
            supportFragmentManager.beginTransaction().replace(R.id.third_panel, fragment).commit()
            slidingPanelLayout!!.closePane()
            this.serviceName = service.displayName
            updateNavigation()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(PARAM_DOMAIN, domain)
        outState.putString(PARAM_REG_TYPE, regType)
        outState.putString(PARAM_SERVICE_NAME, serviceName)
        super.onSaveInstanceState(outState)
    }

    override fun onServiceUpdated(service: BonjourServiceInfo) {
        lastUpdatedTextView?.text = getString(R.string.last_update, Utils.formatTime(System.currentTimeMillis()))
    }

    override fun onServiceStopped(service: BonjourServiceInfo) {
        // Ignore this
    }

    override fun onHttpServerFound(url: URL) {
        // TODO: show FAB
    }

    companion object {
        private const val PARAM_DOMAIN = "param_domain"
        private const val PARAM_REG_TYPE = "param_reg_type"
        private const val PARAM_SERVICE_NAME = "param_service_name"
    }
}

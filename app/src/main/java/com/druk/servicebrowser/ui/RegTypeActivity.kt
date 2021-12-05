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
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.druk.servicebrowser.BonjourApplication
import com.druk.servicebrowser.R
import com.druk.servicebrowser.ui.fragment.ServiceBrowserFragment
import com.druk.servicebrowser.ui.fragment.ServiceBrowserFragment.Companion.newInstance
import com.github.druk.rx2dnssd.BonjourService

class RegTypeActivity : AppCompatActivity(), ServiceBrowserFragment.ServiceListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reg_type)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (intent != null && intent.hasExtra(KEY_DOMAIN) && intent.hasExtra(KEY_REG_TYPE)) {
            val regType = intent.getStringExtra(KEY_REG_TYPE) ?: return
            val domain = intent.getStringExtra(KEY_DOMAIN) ?: return
            val description =
                BonjourApplication.getRegTypeManager(this).getRegTypeDescription(regType)
            title = description ?: regType
            if (savedInstanceState == null) {
                supportFragmentManager.beginTransaction().replace(
                    R.id.content, newInstance(domain, regType)
                ).commit()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onServiceWasSelected(domain: String, regType: String, service: BonjourService) {
        ServiceActivity.startActivity(this, service)
    }

    companion object {

        private const val KEY_REG_TYPE = "com.druk.servicebrowser.ui.RegTypeActivity.KEY_DOMAIN"
        private const val KEY_DOMAIN = "com.druk.servicebrowser.ui.RegTypeActivity.KEY_REG_TYPE"

        @JvmStatic
        fun createIntent(context: Context?, regType: String?, domain: String?): Intent {
            return Intent(context, RegTypeActivity::class.java)
                .putExtra(KEY_DOMAIN, domain)
                .putExtra(KEY_REG_TYPE, regType)
        }
    }
}
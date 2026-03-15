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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.druk.servicebrowser.BonjourApplication
import com.druk.servicebrowser.BonjourServiceInfo
import com.druk.servicebrowser.R
import com.druk.servicebrowser.RegistrationManager
import com.druk.servicebrowser.ui.adapter.ServiceAdapter
import kotlinx.coroutines.launch

class RegistrationsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blank_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, RegistrationsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class RegistrationsFragment : Fragment() {

        private lateinit var adapter: ServiceAdapter
        private lateinit var mRecyclerView: RecyclerView
        private lateinit var mNoServiceView: View

        private val registerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val bonjourServiceInfo = RegisterServiceActivity.parseResult(result.data!!)
                if (bonjourServiceInfo != null) {
                    lifecycleScope.launch {
                        try {
                            BonjourApplication.getRegistrationManager(requireContext())
                                .register(bonjourServiceInfo)
                            updateServices()
                        } catch (e: RegistrationManager.RegistrationException) {
                            Toast.makeText(
                                context,
                                "Error: registration failed (${e.errorCode})",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }

        private val stopLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val bonjourServiceInfo = ServiceActivity.parseResult(result.data!!)
                if (bonjourServiceInfo != null) {
                    BonjourApplication.getRegistrationManager(requireContext()).unregister(bonjourServiceInfo)
                    updateServices()
                }
            }
        }

        override fun onAttach(context: Context) {
            super.onAttach(context)
            adapter = object : ServiceAdapter(context) {
                override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                    holder.text1.text = getItem(position).displayName
                    holder.text2.text = getItem(position).regType
                    holder.itemView.setOnClickListener {
                        stopLauncher.launch(ServiceActivity.startActivity(requireContext(), getItem(position), true))
                    }
                }
            }
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val view = inflater.inflate(R.layout.fragment_registrations, container, false)
            mRecyclerView = view.findViewById(R.id.recycler_view)
            mRecyclerView.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
            mRecyclerView.adapter = adapter
            mNoServiceView = view.findViewById(R.id.no_service)
            view.findViewById<View>(R.id.fab).setOnClickListener {
                registerLauncher.launch(RegisterServiceActivity.createIntent(requireContext()))
            }
            updateServices()
            return view
        }

        private fun updateServices() {
            val registeredServices = BonjourApplication.getRegistrationManager(requireContext()).getRegisteredServices()
            adapter.swap(registeredServices)
            mNoServiceView.visibility = if (registeredServices.isNotEmpty()) View.GONE else View.VISIBLE
        }
    }

    companion object {
        fun startActivity(context: Context) {
            context.startActivity(Intent(context, RegistrationsActivity::class.java))
        }
    }
}

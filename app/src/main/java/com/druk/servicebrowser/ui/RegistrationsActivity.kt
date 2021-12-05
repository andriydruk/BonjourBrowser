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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.druk.servicebrowser.BonjourApplication.Companion.getRegistrationManager
import com.druk.servicebrowser.R
import com.druk.servicebrowser.ui.RegisterServiceActivity.Companion.createIntent
import com.druk.servicebrowser.ui.ServiceActivity.Companion.startActivity
import com.druk.servicebrowser.ui.adapter.ServiceAdapter
import com.github.druk.rx2dnssd.BonjourService
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable

class RegistrationsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blank_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction().replace(R.id.content, RegistrationsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    class RegistrationsFragment : Fragment() {

        private lateinit var adapter: ServiceAdapter
        private lateinit var mRecyclerView: RecyclerView
        private lateinit var mNoServiceView: View

        private var mDisposable: Disposable? = null

        override fun onAttach(context: Context) {
            super.onAttach(context)
            adapter = object : ServiceAdapter(requireContext()) {
                override fun onBindViewHolder(holder: ViewHolder, position: Int) {
                    holder.text1.text = getItem(position).serviceName
                    holder.text2.text = getItem(position).regType
                    holder.itemView.setOnClickListener {
                        startActivityForResult(
                            startActivity(
                                getContext()!!, getItem(position), true
                            ), STOP_REQUEST_CODE
                        )
                    }
                }
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == REGISTER_REQUEST_CODE) {
                if (resultCode == RESULT_OK) {
                    val bonjourService = RegisterServiceActivity.parseResult(data!!) ?: return
                    mDisposable = getRegistrationManager(requireContext())
                        .register(requireContext(), bonjourService)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ updateServices() }) { throwable: Throwable ->
                            Toast.makeText(
                                this@RegistrationsFragment.context,
                                "Error: " + throwable.message,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }
                return
            } else if (requestCode == STOP_REQUEST_CODE) {
                if (resultCode == RESULT_OK) {
                    val bonjourService = ServiceActivity.parseResult(data!!) ?: return
                    getRegistrationManager(context!!).unregister(bonjourService)
                    updateServices()
                }
                return
            }
            super.onActivityResult(requestCode, resultCode, data)
        }

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {
            val view = inflater.inflate(R.layout.fragment_registrations, container, false)
            mRecyclerView = view.findViewById(R.id.recycler_view)
            mRecyclerView.layoutManager = LinearLayoutManager(
                view.context,
                LinearLayoutManager.VERTICAL,
                false
            )
            mRecyclerView.adapter = adapter
            mNoServiceView = view.findViewById(R.id.no_service)
            view.findViewById<View>(R.id.fab).setOnClickListener {
                this@RegistrationsFragment.startActivityForResult(
                    createIntent(
                        context
                    ), REGISTER_REQUEST_CODE
                )
            }
            updateServices()
            return view
        }

        override fun onStop() {
            super.onStop()
            if (mDisposable != null && !mDisposable!!.isDisposed) {
                mDisposable?.dispose()
            }
        }

        private fun updateServices() {
            val registeredServices = getRegistrationManager(requireContext()).registeredServices
            adapter.swap(registeredServices)
            mNoServiceView.visibility =
                if (registeredServices.size > 0) View.GONE else View.VISIBLE
        }

        companion object {
            private const val REGISTER_REQUEST_CODE = 100
            private const val STOP_REQUEST_CODE = 101
        }
    }

    companion object {
        fun startActivity(context: Context) {
            context.startActivity(Intent(context, RegistrationsActivity::class.java))
        }
    }
}
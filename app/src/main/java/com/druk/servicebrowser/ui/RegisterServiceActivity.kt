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
import android.text.TextUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.collection.ArrayMap
import androidx.core.content.IntentCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.druk.servicebrowser.BonjourApplication
import com.druk.servicebrowser.BonjourServiceInfo
import com.druk.servicebrowser.R
import com.druk.servicebrowser.ui.adapter.TxtRecordsAdapter

class RegisterServiceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blank_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, RegisterServiceFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    internal fun setResult(bonjourServiceInfo: BonjourServiceInfo) {
        setResult(Activity.RESULT_OK, Intent().putExtra(SERVICE, bonjourServiceInfo))
        finish()
    }

    class RegisterServiceFragment : Fragment(), TextView.OnEditorActionListener, View.OnClickListener {

        private lateinit var serviceNameEditText: EditText
        private lateinit var regTypeEditText: AppCompatAutoCompleteTextView
        private lateinit var portEditText: EditText
        private lateinit var adapter: TxtRecordsAdapter
        private val mRecords = ArrayMap<String, String>()

        override fun onAttach(context: Context) {
            super.onAttach(context)
            @Suppress("DEPRECATION")
            setHasOptionsMenu(true)
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val view = inflater.inflate(R.layout.fragment_register_service, container, false)
            serviceNameEditText = view.findViewById(R.id.service_name)
            regTypeEditText = view.findViewById(R.id.reg_type)
            portEditText = view.findViewById(R.id.port)

            serviceNameEditText.setOnEditorActionListener(this)
            regTypeEditText.setOnEditorActionListener(this)
            portEditText.setOnEditorActionListener(this)

            adapter = object : TxtRecordsAdapter() {
                override fun onItemClick(view: View, position: Int) {
                    val key = getKey(position)
                    val value = getValue(position)
                    AlertDialog.Builder(requireActivity())
                        .setMessage("Do you really want to delete $key=$value ?")
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                            mRecords.remove(key)
                            adapter.swapTXTRecords(mRecords)
                            adapter.notifyDataSetChanged()
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .create().show()
                }
            }

            val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
            recyclerView.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
            recyclerView.adapter = adapter

            val regTypes = BonjourApplication.getRegTypeManager(requireContext()).getListRegTypes()
            regTypeEditText.setAdapter(ArrayAdapter(requireContext(), android.R.layout.select_dialog_item, regTypes))

            view.findViewById<View>(R.id.fab).setOnClickListener(this)

            return view
        }

        @Suppress("DEPRECATION")
        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.menu_registered_services, menu)
        }

        @Suppress("DEPRECATION")
        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            if (item.itemId == R.id.action_add) {
                val view = requireActivity().layoutInflater.inflate(R.layout.dialog_add_txt_records, null)
                val keyTextView = view.findViewById<TextView>(R.id.key)
                val valueTextView = view.findViewById<TextView>(R.id.value)
                AlertDialog.Builder(requireActivity())
                    .setMessage("Add TXT record")
                    .setView(view)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        mRecords[keyTextView.text.toString()] = valueTextView.text.toString()
                        adapter.swapTXTRecords(mRecords)
                        adapter.notifyDataSetChanged()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .create().show()
                return true
            }
            return super.onOptionsItemSelected(item)
        }

        override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent?): Boolean {
            if (view == null || actionId != EditorInfo.IME_ACTION_DONE) return false
            return when (v.id) {
                R.id.service_name -> {
                    regTypeEditText.requestFocus()
                    true
                }
                R.id.reg_type -> {
                    portEditText.requestFocus()
                    true
                }
                R.id.port -> {
                    val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(requireView().windowToken, 0)
                    true
                }
                else -> false
            }
        }

        override fun onClick(v: View) {
            if (view == null) return

            val serviceName = serviceNameEditText.text.toString()
            val reqType = regTypeEditText.text.toString()
            val port = portEditText.text.toString()
            var portNumber = 0

            var isValid = true
            if (TextUtils.isEmpty(serviceName)) {
                isValid = false
                serviceNameEditText.error = "Service name can't be unspecified"
            }
            if (TextUtils.isEmpty(reqType)) {
                isValid = false
                regTypeEditText.error = "Reg type can't be unspecified"
            }
            if (TextUtils.isEmpty(port)) {
                isValid = false
                portEditText.error = "Port can't be unspecified"
            } else {
                try {
                    portNumber = port.toInt()
                    if (portNumber < 0 || portNumber > 65535) {
                        isValid = false
                        portEditText.error = "Invalid port number (0-65535)"
                    }
                } catch (e: NumberFormatException) {
                    isValid = false
                    portEditText.error = "Invalid port number (0-65535)"
                }
            }

            if (isValid) {
                (activity as? RegisterServiceActivity)?.setResult(
                    BonjourServiceInfo(
                        serviceName = serviceName,
                        regType = reqType,
                        port = portNumber,
                        txtRecords = mRecords.toMap()
                    )
                )
            }
        }
    }

    companion object {
        private const val SERVICE = "service"

        fun createIntent(context: Context): Intent =
            Intent(context, RegisterServiceActivity::class.java)

        fun parseResult(intent: Intent): BonjourServiceInfo? =
            IntentCompat.getParcelableExtra(intent, SERVICE, BonjourServiceInfo::class.java)
    }
}

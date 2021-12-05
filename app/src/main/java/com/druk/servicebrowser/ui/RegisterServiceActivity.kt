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
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import androidx.collection.ArrayMap
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.druk.servicebrowser.BonjourApplication.Companion.getRegTypeManager
import com.druk.servicebrowser.R
import com.druk.servicebrowser.ui.adapter.TxtRecordsAdapter
import com.github.druk.rx2dnssd.BonjourService

class RegisterServiceActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.blank_activity)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, RegisterServiceFragment()).commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setResult(bonjourService: BonjourService) {
        setResult(RESULT_OK, Intent().putExtra(SERVICE, bonjourService))
        finish()
    }

    class RegisterServiceFragment : Fragment(),
        TextView.OnEditorActionListener,
        View.OnClickListener {

        private lateinit var serviceNameEditText: EditText
        private lateinit var regTypeEditText: AppCompatAutoCompleteTextView
        private lateinit var portEditText: EditText
        private lateinit var adapter: TxtRecordsAdapter

        private val mRecords = ArrayMap<String, String>()

        override fun onAttach(context: Context) {
            super.onAttach(context)
            setHasOptionsMenu(true)
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
            val view = inflater.inflate(R.layout.fragment_register_service, container, false)
            serviceNameEditText = view.findViewById(R.id.service_name)
            regTypeEditText = view.findViewById(R.id.reg_type)
            portEditText = view.findViewById(R.id.port)
            serviceNameEditText.setOnEditorActionListener(this)
            regTypeEditText.setOnEditorActionListener(this)
            portEditText.setOnEditorActionListener(this)
            adapter = object : TxtRecordsAdapter(requireContext()) {
                override fun onItemClick(view: View, position: Int) {
                    val builder = AlertDialog.Builder(requireContext())
                    val key = getKey(position)
                    val value = getValue(position)
                    // Inflate and set the layout for the dialog
                    // Pass null as the parent view because its going in the dialog layout
                    builder.setMessage("Do you really want to delete $key=$value ?")
                        .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                            mRecords.remove(key)
                            adapter.swapTXTRecords(mRecords)
                            adapter.notifyDataSetChanged()
                        }
                        .setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int -> }
                    builder.create().show()
                }
            }
            val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
            recyclerView.layoutManager = LinearLayoutManager(view.context, LinearLayoutManager.VERTICAL, false)
            recyclerView.adapter = adapter
            val regTypes = getRegTypeManager(requireContext()).listRegTypes
            regTypeEditText.setAdapter(
                ArrayAdapter(requireContext(), android.R.layout.select_dialog_item, regTypes)
            )
            view.findViewById<View>(R.id.fab).setOnClickListener(this)
            return view
        }

        override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
            inflater.inflate(R.menu.menu_registered_services, menu)
        }

        override fun onOptionsItemSelected(item: MenuItem): Boolean {
            // Handle action bar item clicks here. The action bar will
            // automatically handle clicks on the Home/Up button, so long
            // as you specify a parent activity in AndroidManifest.xml.
            val id = item.itemId
            if (id == R.id.action_add) {
                val builder = AlertDialog.Builder(requireContext())
                val view = activity?.layoutInflater?.inflate(R.layout.dialog_add_txt_records, null) ?: return false
                val keyTextView = view.findViewById<TextView>(R.id.key)
                val valueTextView = view.findViewById<TextView>(R.id.value)
                // Inflate and set the layout for the dialog
                // Pass null as the parent view because its going in the dialog layout
                builder.setMessage("Add TXT record")
                    .setView(view)
                    .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                        mRecords[keyTextView.text.toString()] = valueTextView.text.toString()
                        adapter.swapTXTRecords(mRecords)
                        adapter.notifyDataSetChanged()
                    }
                    .setNegativeButton(android.R.string.cancel) { _: DialogInterface?, _: Int -> }
                builder.create().show()
                return true
            }
            return super.onOptionsItemSelected(item)
        }

        override fun onEditorAction(v: TextView, actionId: Int, event: KeyEvent): Boolean {
            if (view == null || actionId != EditorInfo.IME_ACTION_DONE) {
                return false
            }
            when (v.id) {
                R.id.service_name -> {
                    regTypeEditText.requestFocus()
                    return true
                }
                R.id.reg_type -> {
                    portEditText.requestFocus()
                    return true
                }
                R.id.port -> {
                    val imm = requireActivity().getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view!!.windowToken, 0)
                    return true
                }
            }
            return false
        }

        override fun onClick(v: View) {
            if (view == null) {
                return
            }
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
                if (activity is RegisterServiceActivity) {
                    (activity as RegisterServiceActivity?)!!.setResult(
                        BonjourService.Builder(0, 0, serviceName, reqType, null).port(portNumber)
                            .dnsRecords(mRecords).build()
                    )
                }
            }
        }
    }

    companion object {
        private const val SERVICE = "service"
        @JvmStatic
        fun createIntent(context: Context?): Intent {
            return Intent(context, RegisterServiceActivity::class.java)
        }

        @JvmStatic
        fun parseResult(intent: Intent): BonjourService? {
            return intent.getParcelableExtra(SERVICE)
        }
    }
}
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
package com.druk.servicebrowser.ui.fragment

import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.druk.servicebrowser.BonjourApplication
import com.druk.servicebrowser.BonjourServiceInfo
import com.druk.servicebrowser.Config
import com.druk.servicebrowser.Config.EMPTY_DOMAIN
import com.druk.servicebrowser.R
import com.druk.servicebrowser.RegTypeManager
import com.druk.servicebrowser.ui.adapter.ServiceAdapter
import com.druk.servicebrowser.ui.viewmodel.BonjourDomain
import com.druk.servicebrowser.ui.viewmodel.RegTypeBrowserViewModel
import kotlinx.coroutines.launch

class RegTypeBrowserFragment : ServiceBrowserFragment() {

    private lateinit var mRegTypeManager: RegTypeManager

    /** Maps BonjourServiceInfo to its service count for display. */
    private val serviceCounts = HashMap<BonjourServiceInfo, Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mRegTypeManager = BonjourApplication.getRegTypeManager(requireContext())

        val drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_star)

        mAdapter = object : ServiceAdapter(requireActivity()) {
            override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
                val info = getItem(i)
                val count = serviceCounts[info] ?: 0

                val regType = "${info.displayName}.${info.regType?.split(Config.REG_TYPE_SEPARATOR)?.get(0)}."
                val regTypeDescription = mRegTypeManager.getRegTypeDescription(regType)
                viewHolder.text1.text = if (regTypeDescription != null) "$regType ($regTypeDescription)" else regType

                if (favouritesManager.isFavourite(regType)) {
                    viewHolder.text1.setCompoundDrawablesWithIntrinsicBounds(null, null, drawable, null)
                } else {
                    viewHolder.text1.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                }

                viewHolder.text2.text = "$count services"
                viewHolder.itemView.setOnClickListener(mListener)
            }

            override fun sortServices(services: ArrayList<BonjourServiceInfo>) {
                services.sortWith(Comparator { lhs, rhs ->
                    val lhsRegType = "${lhs.displayName}.${lhs.regType?.split(Config.REG_TYPE_SEPARATOR)?.get(0)}."
                    val rhsRegType = "${rhs.displayName}.${rhs.regType?.split(Config.REG_TYPE_SEPARATOR)?.get(0)}."
                    val isLhsFavourite = favouritesManager.isFavourite(lhsRegType)
                    val isRhsFavourite = favouritesManager.isFavourite(rhsRegType)
                    when {
                        isLhsFavourite && isRhsFavourite -> lhs.displayName.compareTo(rhs.displayName)
                        isLhsFavourite -> -1
                        isRhsFavourite -> 1
                        else -> lhs.displayName.compareTo(rhs.displayName)
                    }
                })
            }
        }
    }

    override fun createViewModel() {
        val viewModel: RegTypeBrowserViewModel by viewModels()

        viewLifecycleOwnerLiveData.observe(this) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        viewModel.services.collect { services ->
                            serviceCounts.clear()
                            mAdapter.clear()
                            for (bonjourDomain in services) {
                                if (bonjourDomain.serviceCount > 0) {
                                    serviceCounts[bonjourDomain.info] = bonjourDomain.serviceCount
                                    mAdapter.add(bonjourDomain.info)
                                }
                            }
                            showList()
                            mAdapter.notifyDataSetChanged()
                        }
                    }
                    launch {
                        viewModel.error.collect { throwable ->
                            showError(throwable)
                        }
                    }
                }
            }
        }

        viewModel.startDiscovery()
    }

    override fun favouriteMenuSupport(): Boolean = false

    override fun onStart() {
        super.onStart()
        mAdapter.sortServices()
        mAdapter.notifyDataSetChanged()
    }

    companion object {
        fun newInstance(regType: String): Fragment =
            fillArguments(RegTypeBrowserFragment(), EMPTY_DOMAIN, regType)
    }
}

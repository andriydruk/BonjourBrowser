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

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import com.druk.servicebrowser.RegTypeManager
import com.druk.servicebrowser.BonjourApplication
import com.druk.servicebrowser.ui.adapter.ServiceAdapter
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.druk.servicebrowser.Config
import com.druk.servicebrowser.R
import com.druk.servicebrowser.ui.viewmodel.RegTypeBrowserViewModel.BonjourDomain
import com.github.druk.rx2dnssd.BonjourService
import com.druk.servicebrowser.ui.viewmodel.RegTypeBrowserViewModel
import io.reactivex.functions.Consumer
import java.util.*

class RegTypeBrowserFragment : ServiceBrowserFragment() {

    private var mRegTypeManager: RegTypeManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mRegTypeManager = BonjourApplication.getRegTypeManager(context!!)
        mAdapter = object : ServiceAdapter(requireActivity()) {

            var drawable = ContextCompat.getDrawable(requireContext(), R.drawable.ic_star_accent)

            @SuppressLint("SetTextI18n")
            override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
                val domain = getItem(i) as BonjourDomain
                val regType =
                    domain.serviceName + "." + domain.regType.split(Config.REG_TYPE_SEPARATOR)
                        .toTypedArray()[0] + "."
                val regTypeDescription = mRegTypeManager?.getRegTypeDescription(regType)
                if (regTypeDescription != null) {
                    viewHolder.text1.text = "$regType ($regTypeDescription)"
                } else {
                    viewHolder.text1.text = regType
                }
                if (favouritesManager.isFavourite(regType)) {
                    viewHolder.text1.setCompoundDrawablesWithIntrinsicBounds(
                        null,
                        null,
                        drawable,
                        null
                    )
                } else {
                    viewHolder.text1.setCompoundDrawablesWithIntrinsicBounds(null,
                        null,
                        null,
                        null)
                }
                viewHolder.text2.text = "${domain.serviceCount} services"
                viewHolder.itemView.setOnClickListener(mListener)
                viewHolder.itemView.setBackgroundResource(getBackground(i))
            }

            override fun sortServices(services: ArrayList<BonjourService>) {
                services.sortWith { lhs: BonjourService, rhs: BonjourService ->
                    val lhsRegType = lhs.serviceName + "." + lhs.regType.split(
                        Config.REG_TYPE_SEPARATOR
                    ).toTypedArray()[0] + "."
                    val rhsRegType =
                        rhs.serviceName + "." + rhs.regType.split(Config.REG_TYPE_SEPARATOR)
                            .toTypedArray()[0] + "."
                    val isLhsFavourite = favouritesManager.isFavourite(lhsRegType)
                    val isRhsFavourite = favouritesManager.isFavourite(rhsRegType)
                    if (isLhsFavourite && isRhsFavourite) {
                        return@sortWith lhs.serviceName.compareTo(rhs.serviceName)
                    } else if (isLhsFavourite) {
                        return@sortWith -1
                    } else if (isRhsFavourite) {
                        return@sortWith 1
                    }
                    lhs.serviceName.compareTo(rhs.serviceName)
                }
            }
        }
    }

    override fun createViewModel() {
        val viewModel: RegTypeBrowserViewModel by viewModels()
        val errorAction = Consumer { throwable: Throwable ->
            Log.e("DNSSD", "Error: ", throwable)
            showError(throwable)
        }
        val servicesAction = Consumer { services: Collection<BonjourDomain> ->
            mAdapter.clear()
            for (bonjourDomain in services) {
                if (bonjourDomain.serviceCount > 0) {
                    mAdapter.add(bonjourDomain)
                }
            }
            showList()
            mAdapter.notifyDataSetChanged()
        }
        viewModel.startDiscovery(servicesAction, errorAction)
    }

    override fun favouriteMenuSupport(): Boolean {
        return false
    }

    override fun onStart() {
        super.onStart()
        // Favourites can be changed
        mAdapter.sortServices()
        mAdapter.notifyDataSetChanged()
    }

    companion object {
        @JvmStatic
        fun newInstance(regType: String): Fragment {
            return fillArguments(RegTypeBrowserFragment(), Config.EMPTY_DOMAIN, regType)
        }
    }
}
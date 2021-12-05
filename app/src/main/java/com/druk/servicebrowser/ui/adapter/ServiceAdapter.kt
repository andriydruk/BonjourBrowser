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
package com.druk.servicebrowser.ui.adapter

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.druk.servicebrowser.R
import com.github.druk.rx2dnssd.BonjourService
import java.util.*

abstract class ServiceAdapter protected constructor(context: Context) :
    RecyclerView.Adapter<ServiceAdapter.ViewHolder>() {

    private val mSelectedBackground: Int
    private val mBackground: Int
    private val services = ArrayList<BonjourService>()

    var selectedItemId: Long = -1

    init {
        val mTypedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.selectableItemBackground, mTypedValue, true)
        mBackground = mTypedValue.resourceId
        context.theme.resolveAttribute(R.attr.selectedItemBackground, mTypedValue, true)
        mSelectedBackground = mTypedValue.resourceId
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(viewGroup.context).inflate(R.layout.two_text_item, viewGroup, false)
        )
    }

    override fun getItemCount(): Int {
        return services.size
    }

    override fun getItemId(position: Int): Long {
        return services[position].hashCode().toLong()
    }

    fun getItem(position: Int): BonjourService {
        return services[position]
    }

    fun clear() {
        services.clear()
    }

    protected fun getBackground(position: Int): Int {
        return if (getItemId(position) == selectedItemId) mSelectedBackground else mBackground
    }

    fun add(service: BonjourService) {
        services.remove(service)
        services.add(service)
        sortServices(services)
    }

    fun swap(service: List<BonjourService>?) {
        services.clear()
        services.addAll(service!!)
        sortServices(services)
        notifyDataSetChanged()
    }

    fun remove(bonjourService: BonjourService) {
        if (services.remove(bonjourService)) {
            sortServices(services)
        }
    }

    fun sortServices() {
        sortServices(services)
    }

    open fun sortServices(services: ArrayList<BonjourService>) {
        services.sortWith { lhs: BonjourService, rhs: BonjourService ->
            lhs.serviceName.compareTo(
                rhs.serviceName
            )
        }
    }

    class ViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
        var text1: TextView = itemView.findViewById(R.id.text1)
        var text2: TextView = itemView.findViewById(R.id.text2)
    }
}
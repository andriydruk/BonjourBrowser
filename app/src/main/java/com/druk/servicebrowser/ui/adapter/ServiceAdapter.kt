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
import com.druk.servicebrowser.BonjourServiceInfo
import com.druk.servicebrowser.R

abstract class ServiceAdapter(context: Context) : RecyclerView.Adapter<ServiceAdapter.ViewHolder>() {

    private val selectedBackground: Int
    private val background: Int
    private val services = ArrayList<BonjourServiceInfo>()

    var selectedItemId: Long = -1

    init {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.colorSurface, typedValue, true)
        background = typedValue.resourceId

        context.theme.resolveAttribute(R.attr.colorPrimaryContainer, typedValue, true)
        selectedBackground = typedValue.resourceId
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.two_text_item, viewGroup, false))

    override fun getItemCount(): Int = services.size

    override fun getItemId(position: Int): Long = services[position].hashCode().toLong()

    fun getItem(position: Int): BonjourServiceInfo = services[position]

    fun clear() {
        services.clear()
    }

    protected fun getBackground(position: Int): Int =
        if (getItemId(position) == selectedItemId) selectedBackground else background

    fun add(service: BonjourServiceInfo) {
        services.remove(service)
        services.add(service)
        sortServices(services)
    }

    fun swap(service: List<BonjourServiceInfo>) {
        services.clear()
        services.addAll(service)
        sortServices(services)
        notifyDataSetChanged()
    }

    fun remove(bonjourService: BonjourServiceInfo) {
        if (services.remove(bonjourService)) {
            sortServices(services)
        }
    }

    fun sortServices() {
        sortServices(services)
    }

    open fun sortServices(services: ArrayList<BonjourServiceInfo>) {
        services.sortWith(compareBy { it.displayName })
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text1: TextView = itemView.findViewById(R.id.text1)
        val text2: TextView = itemView.findViewById(R.id.text2)
    }
}

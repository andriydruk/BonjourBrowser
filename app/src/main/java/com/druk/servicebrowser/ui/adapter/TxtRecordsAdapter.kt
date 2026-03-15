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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.collection.ArrayMap
import androidx.collection.SimpleArrayMap
import androidx.recyclerview.widget.RecyclerView
import com.druk.servicebrowser.R

open class TxtRecordsAdapter : RecyclerView.Adapter<TxtRecordsAdapter.ViewHolder>() {

    private val ipRecords = SimpleArrayMap<String, String>()
    private val txtRecords = SimpleArrayMap<String, String>()

    override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.two_text_item, viewGroup, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.text1.text = getKey(position)
        holder.text2.text = getValue(position)
        holder.itemView.setOnClickListener { v -> onItemClick(v, position) }
    }

    open fun onItemClick(view: View, position: Int) {
        val context = view.context
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(getKey(position), getValue(position))
        clipboard.setPrimaryClip(clip)
    }

    override fun getItemCount(): Int = ipRecords.size() + txtRecords.size()

    fun getKey(position: Int): String =
        if (position < ipRecords.size()) ipRecords.keyAt(position)
        else txtRecords.keyAt(position - ipRecords.size())

    fun getValue(position: Int): String =
        if (position < ipRecords.size()) ipRecords.valueAt(position)
        else txtRecords.valueAt(position - ipRecords.size())

    fun swapIPRecords(records: ArrayMap<String, String>) {
        ipRecords.clear()
        ipRecords.putAll(records)
    }

    fun swapTXTRecords(records: ArrayMap<String, String>) {
        txtRecords.clear()
        txtRecords.putAll(records)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text1: TextView = itemView.findViewById(R.id.text1)
        val text2: TextView = itemView.findViewById(R.id.text2)
    }
}

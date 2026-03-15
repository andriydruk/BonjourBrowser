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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatTextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.druk.servicebrowser.R

class LicensesActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var mLayoutManager: LinearLayoutManager
    private lateinit var mAdapter: OpenSourceComponentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_license)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mLayoutManager = LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        mAdapter = OpenSourceComponentAdapter(
            LICENSE_SOFTWARE,
            arrayOf(
                ANDROID_ASSETS_FILE_PATH + ANDROID_OPEN_SOURCE_PROJECT_LICENSE,
                ANDROID_ASSETS_FILE_PATH + ANDROID_OPEN_SOURCE_PROJECT_LICENSE,
                ANDROID_ASSETS_FILE_PATH + ANDROID_OPEN_SOURCE_PROJECT_LICENSE,
                ANDROID_ASSETS_FILE_PATH + ANDROID_SOFTWARE_DEVELOPMENT_KIT
            )
        )

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = mLayoutManager
        recyclerView.adapter = mAdapter
    }

    override fun onResume() {
        super.onResume()
        mAdapter.listener = this
    }

    override fun onPause() {
        super.onPause()
        mAdapter.listener = null
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onClick(v: View) {
        val position = mLayoutManager.getPosition(v)
        val intent = Intent(v.context, HTMLViewerActivity::class.java).apply {
            data = Uri.parse(mAdapter.getLicensePath(position))
            putExtra(Intent.EXTRA_TITLE, mAdapter.getComponentName(position))
            addCategory(Intent.CATEGORY_DEFAULT)
        }

        try {
            v.context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.e("TAG", "Failed to find viewer", e)
        }
    }

    private class OpenSourceComponentAdapter(
        private val componentNames: Array<String>,
        private val licensePaths: Array<String>
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        var listener: View.OnClickListener? = null

        override fun onCreateViewHolder(viewGroup: ViewGroup, i: Int): RecyclerView.ViewHolder {
            return object : RecyclerView.ViewHolder(
                LayoutInflater.from(viewGroup.context).inflate(R.layout.one_text_item, viewGroup, false)
            ) {}
        }

        override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, i: Int) {
            (viewHolder.itemView as AppCompatTextView).text = componentNames[i]
            viewHolder.itemView.setOnClickListener(listener)
        }

        override fun getItemCount(): Int = componentNames.size

        fun getComponentName(position: Int): String = componentNames[position]

        fun getLicensePath(position: Int): String = licensePaths[position]
    }

    companion object {
        private val LICENSE_SOFTWARE = arrayOf(
            "Android Compatibility Library v4",
            "Android Compatibility Library v7",
            "Android Design Support Library",
            "Android SDK"
        )

        private const val ANDROID_ASSETS_FILE_PATH = "file:///android_asset/"
        private const val ANDROID_OPEN_SOURCE_PROJECT_LICENSE = "ANDROID-OPEN-SOURCE-PROJECT-LICENSE.txt"
        private const val ANDROID_SOFTWARE_DEVELOPMENT_KIT = "ANDROID-SOFTWARE-DEVELOPMENT-KIT.txt"
    }
}

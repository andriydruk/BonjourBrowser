package com.druk.servicebrowser.ui

/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.druk.servicebrowser.R
import java.net.URISyntaxException

class HTMLViewerActivity : AppCompatActivity() {

    private lateinit var mWebView: WebView
    private lateinit var mLoading: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_html_viewer)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        mWebView = findViewById(R.id.webview)
        mLoading = findViewById(R.id.loading)

        mWebView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!intent.hasExtra(Intent.EXTRA_TITLE)) {
                    this@HTMLViewerActivity.title = title
                }
            }
        }

        mWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                mLoading.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val intent = try {
                    Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                } catch (ex: URISyntaxException) {
                    Log.w(TAG, "Bad URI $url: ${ex.message}")
                    Toast.makeText(this@HTMLViewerActivity, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
                    return true
                }

                intent.addCategory(Intent.CATEGORY_BROWSABLE)
                intent.component = null
                intent.selector?.let { selector ->
                    selector.addCategory(Intent.CATEGORY_BROWSABLE)
                    selector.component = null
                }

                try {
                    view.context.startActivity(intent)
                } catch (ex: ActivityNotFoundException) {
                    Log.w(TAG, "No application can handle $url")
                    Toast.makeText(this@HTMLViewerActivity, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }

        mWebView.settings.apply {
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            blockNetworkLoads = true
            javaScriptEnabled = false
            defaultTextEncodingName = "utf-8"
        }

        if (intent.hasExtra(Intent.EXTRA_TITLE)) {
            title = intent.getStringExtra(Intent.EXTRA_TITLE)
        }

        mWebView.loadUrl(intent.data.toString())
    }

    override fun onDestroy() {
        super.onDestroy()
        mWebView.destroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        private const val TAG = "HTMLViewer"
    }
}

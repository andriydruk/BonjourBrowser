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

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.druk.servicebrowser.BonjourApplication
import com.druk.servicebrowser.BuildConfig
import com.druk.servicebrowser.FavouritesManager
import com.druk.servicebrowser.R
import com.druk.servicebrowser.ui.adapter.ServiceAdapter
import com.druk.servicebrowser.ui.viewmodel.ServiceBrowserViewModel
import com.github.druk.rx2dnssd.BonjourService

open class ServiceBrowserFragment : Fragment() {

    private lateinit var mReqType: String
    protected lateinit var mDomain: String

    protected lateinit var favouritesManager: FavouritesManager
    protected lateinit var mAdapter: ServiceAdapter
    protected lateinit var mRecyclerView: RecyclerView
    protected lateinit var mProgressView: LinearLayout
    protected lateinit var mErrorView: LinearLayout

    protected var mListener = View.OnClickListener { v ->
        val position = mRecyclerView.layoutManager?.getPosition(v) ?: 0
        mAdapter.selectedItemId = mAdapter.getItemId(position)
        mAdapter.notifyDataSetChanged()
        if (this@ServiceBrowserFragment.isAdded) {
            val service = mAdapter.getItem(position)
            (this@ServiceBrowserFragment.activity as ServiceListener).onServiceWasSelected(
                mDomain,
                mReqType,
                service
            )
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        require(context is ServiceListener) { "Fragment context should implement ServiceListener interface" }
        favouritesManager = BonjourApplication.getFavouritesManager(context)
        mReqType = arguments?.getString(KEY_REG_TYPE) ?: return
        mDomain = arguments?.getString(KEY_DOMAIN) ?: return
        mAdapter = object : ServiceAdapter(requireActivity()) {
            override fun onBindViewHolder(viewHolder: ViewHolder, i: Int) {
                val service = getItem(i)
                viewHolder.text1.text = service.serviceName
                when {
                    service.inet4Address != null -> {
                        viewHolder.text2.text = service.inet4Address!!.hostAddress
                    }
                    service.inet6Address != null -> {
                        viewHolder.text2.text = service.inet6Address!!.hostAddress
                    }
                    else -> {
                        viewHolder.text2.setText(R.string.not_resolved_yet)
                    }
                }
                viewHolder.itemView.setOnClickListener(mListener)
                viewHolder.itemView.setBackgroundResource(getBackground(i))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createViewModel()
        setHasOptionsMenu(favouriteMenuSupport())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        inflater.inflate(R.menu.menu_domain, menu)
        val item = menu.findItem(R.id.action_star)
        val isFavourite = favouritesManager.isFavourite(mReqType)
        item.isChecked = isFavourite
        item.setIcon(if (isFavourite) R.drawable.ic_star else R.drawable.ic_star_border)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_star) {
            if (!item.isChecked) {
                favouritesManager.addToFavourites(mReqType)
                item.isChecked = true
                item.setIcon(R.drawable.ic_star)
                Toast.makeText(context, "$mReqType saved to Favourites", Toast.LENGTH_LONG).show()
            } else {
                favouritesManager.removeFromFavourites(mReqType)
                item.isChecked = false
                item.setIcon(R.drawable.ic_star_border)
                Toast.makeText(context, "$mReqType removed from Favourites", Toast.LENGTH_LONG)
                    .show()
            }
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    protected open fun favouriteMenuSupport(): Boolean {
        return true
    }

    protected open fun createViewModel() {
        val viewModel: ServiceBrowserViewModel by viewModels()
        viewModel.startDiscovery(mReqType, mDomain, { service: BonjourService ->
            if (!service.isLost) {
                mAdapter.add(service)
            } else {
                mAdapter.remove(service)
            }
            showList()
            mAdapter.notifyDataSetChanged()
        }) { throwable: Throwable ->
            Log.e("DNSSD", "Error: ", throwable)
            showError(throwable)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView =
            inflater.inflate(R.layout.fragment_service_browser, container, false) as FrameLayout
        mRecyclerView = rootView.findViewById(R.id.recycler_view)
        mProgressView = rootView.findViewById(R.id.progress)
        mErrorView = rootView.findViewById(R.id.error_container)
        mRecyclerView.layoutManager = LinearLayoutManager(mRecyclerView.context)
        mRecyclerView.setHasFixedSize(true)
        mRecyclerView.adapter = mAdapter
        if (savedInstanceState != null) {
            mAdapter.selectedItemId =
                savedInstanceState.getLong(KEY_SELECTED_POSITION, -1L)
        }
        return rootView
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_SELECTED_POSITION, mAdapter.selectedItemId)
    }

    protected fun showList() {
        if (mAdapter.itemCount > 0) {
            mRecyclerView.visibility = View.VISIBLE
            mProgressView.visibility = View.GONE
        } else {
            mRecyclerView.visibility = View.GONE
            mProgressView.visibility = View.VISIBLE
        }
    }

    protected fun showError(e: Throwable) {
        if (BuildConfig.BUILD_TYPE == "iot") {
            Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(Thread.currentThread(), e)
            return
        }
        activity?.runOnUiThread {
            mRecyclerView.animate().alpha(0.0f)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        mRecyclerView.visibility = View.GONE
                    }
                }).start()
            mProgressView.animate().alpha(0.0f)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        mProgressView.visibility = View.GONE
                    }
                }).start()
            mErrorView.alpha = 0.0f
            mErrorView.visibility = View.VISIBLE
            mErrorView.animate().alpha(1.0f).setInterpolator(AccelerateDecelerateInterpolator())
                .start()
            mErrorView.findViewById<View>(R.id.send_report).setOnClickListener {
                Thread.getDefaultUncaughtExceptionHandler()?.uncaughtException(
                    Thread.currentThread(), e
                )
            }
        }
    }

    interface ServiceListener {
        fun onServiceWasSelected(domain: String, regType: String, service: BonjourService)
    }

    companion object {

        private const val KEY_REG_TYPE = "reg_type"
        private const val KEY_DOMAIN = "domain"
        private const val KEY_SELECTED_POSITION = "selected_position"

        @JvmStatic
        fun newInstance(domain: String, regType: String): Fragment {
            return fillArguments(ServiceBrowserFragment(), domain, regType)
        }

        fun fillArguments(fragment: Fragment, domain: String, regType: String): Fragment {
            val bundle = Bundle()
            bundle.putString(KEY_DOMAIN, domain)
            bundle.putString(KEY_REG_TYPE, regType)
            fragment.arguments = bundle
            return fragment
        }
    }
}
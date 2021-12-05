package com.druk.servicebrowser.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.collection.ArrayMap
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.druk.servicebrowser.R
import com.druk.servicebrowser.ui.adapter.TxtRecordsAdapter
import com.druk.servicebrowser.ui.viewmodel.ServiceDetailViewModel
import com.github.druk.rx2dnssd.BonjourService
import java.net.Inet4Address
import java.net.URL

class ServiceDetailFragment : Fragment(), View.OnClickListener {

    private val viewModel: ServiceDetailViewModel by viewModels()

    private lateinit var mService: BonjourService
    private lateinit var mAdapter: TxtRecordsAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        require(context is ServiceDetailListener) { "Fragment context should implement ServiceDetailListener interface" }
        mService = arguments?.getParcelable(KEY_SERVICE) ?: return
        mAdapter = TxtRecordsAdapter(requireActivity())
        viewModel.resolveIPRecords(mService) { service: BonjourService? ->
            updateIPRecords(
                service
            )
        }
        viewModel.resolveTXTRecords(mService) { service: BonjourService? ->
            updateTXTRecords(
                service
            )
        }
    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        val mRecyclerView =
            inflater.inflate(R.layout.fragment_service_detail, container, false) as RecyclerView
        mRecyclerView.layoutManager = LinearLayoutManager(mRecyclerView.context)
        mRecyclerView.setHasFixedSize(true)
        mRecyclerView.adapter = mAdapter
        updateIPRecords(mService)
        updateTXTRecords(mService)
        (activity as ServiceDetailListener?)!!.onServiceUpdated(mService)
        return mRecyclerView
    }

    private fun updateIPRecords(service: BonjourService?) {
        val metaInfo = ArrayMap<String, String>()
        for (inetAddress in service!!.inetAddresses) {
            if (inetAddress is Inet4Address) {
                metaInfo["Address IPv4"] = service.inet4Address!!
                    .hostAddress + ":" + service.port
            } else {
                metaInfo["Address IPv6"] = service.inet6Address!!
                    .hostAddress + ":" + service.port
            }
        }
        mAdapter.swapIPRecords(metaInfo)
        mAdapter.notifyDataSetChanged()
        if (isAdded) {
            (activity as ServiceDetailListener?)!!.onServiceUpdated(mService)
        }
        viewModel.checkHttpConnection(mService) { url: URL -> onHttpServerFound(url) }
    }

    private fun updateTXTRecords(service: BonjourService?) {
        val metaInfo = ArrayMap<String, String>()
        metaInfo.putAll(service!!.txtRecords)
        mAdapter.swapTXTRecords(metaInfo)
        mAdapter.notifyDataSetChanged()
        if (isAdded) {
            (activity as ServiceDetailListener?)!!.onServiceUpdated(mService)
        }
    }

    private fun onHttpServerFound(url: URL) {
        (activity as ServiceDetailListener).onHttpServerFound(url)
    }

    override fun onClick(v: View) {
        (activity as ServiceDetailListener).onServiceStopped(mService)
    }

    interface ServiceDetailListener {
        fun onServiceUpdated(service: BonjourService)
        fun onHttpServerFound(url: URL)
        fun onServiceStopped(service: BonjourService)
    }

    companion object {

        private const val KEY_SERVICE =
            "com.druk.servicebrowser.ui.fragment.ServiceDetailFragment.key_service"

        @JvmStatic
        fun newInstance(service: BonjourService?): ServiceDetailFragment {
            val fragment = ServiceDetailFragment()
            val bundle = Bundle()
            bundle.putParcelable(KEY_SERVICE, service)
            fragment.arguments = bundle
            return fragment
        }
    }
}
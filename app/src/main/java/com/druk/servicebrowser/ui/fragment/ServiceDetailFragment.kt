package com.druk.servicebrowser.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.collection.ArrayMap
import androidx.core.os.BundleCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.druk.servicebrowser.BonjourServiceInfo
import com.druk.servicebrowser.R
import com.druk.servicebrowser.ui.adapter.TxtRecordsAdapter
import com.druk.servicebrowser.ui.viewmodel.ServiceDetailViewModel
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.URL

class ServiceDetailFragment : Fragment(), View.OnClickListener {

    private var mService: BonjourServiceInfo? = null
    private val viewModel: ServiceDetailViewModel by viewModels()
    private lateinit var mAdapter: TxtRecordsAdapter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        require(context is ServiceDetailListener) {
            "Fragment context should implement ServiceDetailListener interface"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            mService = BundleCompat.getParcelable(args, KEY_SERVICE, BonjourServiceInfo::class.java)
        }
        mAdapter = TxtRecordsAdapter()

        viewLifecycleOwnerLiveData.observe(this) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    launch {
                        viewModel.serviceInfo.collect { service ->
                            if (service != null) {
                                mService = service
                                updateRecords(service)
                            }
                        }
                    }
                    launch {
                        viewModel.httpUrl.collect { url ->
                            if (url != null) {
                                (activity as? ServiceDetailListener)?.onHttpServerFound(url)
                            }
                        }
                    }
                }
            }
        }

        mService?.let { viewModel.resolve(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val recyclerView = inflater.inflate(R.layout.fragment_service_detail, container, false) as RecyclerView
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.setHasFixedSize(true)
        recyclerView.adapter = mAdapter
        mService?.let { updateRecords(it) }
        (activity as? ServiceDetailListener)?.onServiceUpdated(mService!!)
        return recyclerView
    }

    private fun updateRecords(service: BonjourServiceInfo) {
        val ipInfo = ArrayMap<String, String>()
        for (inetAddress in service.inetAddresses) {
            if (inetAddress is Inet4Address) {
                ipInfo["Address IPv4"] = "${inetAddress.hostAddress}:${service.port}"
            } else {
                ipInfo["Address IPv6"] = "${inetAddress.hostAddress}:${service.port}"
            }
        }
        mAdapter.swapIPRecords(ipInfo)

        val txtInfo = ArrayMap<String, String>()
        txtInfo.putAll(service.txtRecords)
        mAdapter.swapTXTRecords(txtInfo)

        mAdapter.notifyDataSetChanged()
        if (isAdded) {
            (activity as? ServiceDetailListener)?.onServiceUpdated(mService!!)
        }
    }

    override fun onClick(v: View) {
        (activity as? ServiceDetailListener)?.onServiceStopped(mService!!)
    }

    interface ServiceDetailListener {
        fun onServiceUpdated(service: BonjourServiceInfo)
        fun onHttpServerFound(url: URL)
        fun onServiceStopped(service: BonjourServiceInfo)
    }

    companion object {
        private const val KEY_SERVICE = "com.druk.servicebrowser.ui.fragment.ServiceDetailFragment.key_service"

        fun newInstance(service: BonjourServiceInfo): ServiceDetailFragment {
            return ServiceDetailFragment().apply {
                arguments = Bundle().apply {
                    putParcelable(KEY_SERVICE, service)
                }
            }
        }
    }
}

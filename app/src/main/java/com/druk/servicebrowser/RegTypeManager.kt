package com.druk.servicebrowser

import android.content.Context
import android.text.TextUtils
import android.util.Log
import io.reactivex.Flowable
import io.reactivex.schedulers.Schedulers
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class RegTypeManager internal constructor(private val mContext: Context) {

    companion object {
        private const val TAG = "RegTypeManager"
    }

    private var mServiceNamesTree: TreeMap<String, String>? = null

    init {
        // Load reg type descriptions as quick as possible on io thread
        Flowable.just("_zigbee-gateway._udp.")
            .map { regType: String -> getRegTypeDescription(regType) }
            .subscribeOn(Schedulers.io())
            .observeOn(Schedulers.computation())
            .subscribe()
    }

    val listRegTypes: List<String>
        get() = if (mServiceNamesTree == null) {
            LinkedList()
        } else LinkedList(mServiceNamesTree!!.keys)

    fun getRegTypeDescription(regType: String): String? {
        if (mServiceNamesTree == null) {
            synchronized(this) {
                if (mServiceNamesTree == null) {
                    mServiceNamesTree = TreeMap()
                    try {
                        val inputStream = mContext.assets.open("service-names-port-numbers.csv")
                        try {
                            val reader = BufferedReader(InputStreamReader(inputStream))
                            var line = reader.readLine()
                            while (line != null) {
                                val rowData = line.split(",").toTypedArray()
                                if (rowData.size < 4
                                    || TextUtils.isEmpty(rowData[0])
                                    || TextUtils.isEmpty(rowData[2])
                                    || TextUtils.isEmpty(rowData[3])) {
                                    continue
                                }
                                if (rowData[0].contains(" ")
                                    || rowData[2].contains(" ")) {
                                    continue
                                }
                                val key = "_" + rowData[0] + "._" + rowData[2] + "."
                                mServiceNamesTree!![key] = rowData[3]
                                line = readLine()
                            }
                        } catch (ex: IOException) {
                            // handle exception
                        } finally {
                            try {
                                inputStream.close()
                            } catch (e: IOException) {
                                Log.e(TAG, "init error: ", e)
                            }
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        Log.e(TAG, "service-names-port-numbers.csv reading error: ", e)
                    }
                }
            }
        }
        return mServiceNamesTree!![regType]
    }
}
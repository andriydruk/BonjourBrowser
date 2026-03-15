package com.druk.servicebrowser

import android.content.Context
import android.text.TextUtils
import android.util.Log
import java.io.IOException
import java.util.TreeMap

class RegTypeManager(private val context: Context) {

    private val serviceNamesTree: TreeMap<String, String> by lazy {
        val tree = TreeMap<String, String>()
        try {
            context.assets.open("service-names-port-numbers.csv").bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val rowData = line.split(",")
                    if (rowData.size < 4 || TextUtils.isEmpty(rowData[0]) || TextUtils.isEmpty(rowData[2]) || TextUtils.isEmpty(rowData[3])) {
                        return@forEachLine
                    }
                    if (rowData[0].contains(" ") || rowData[2].contains(" ")) {
                        return@forEachLine
                    }
                    tree["_${rowData[0]}._${rowData[2]}."] = rowData[3]
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "service-names-port-numbers.csv reading error: ", e)
        }
        tree
    }

    fun getListRegTypes(): List<String> = serviceNamesTree.keys.toList()

    fun getRegTypeDescription(regType: String): String? = serviceNamesTree[regType]

    companion object {
        private const val TAG = "RegTypeManager"
    }
}

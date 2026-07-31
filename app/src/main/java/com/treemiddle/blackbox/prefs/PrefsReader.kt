package com.treemiddle.blackbox.prefs

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PrefsReader {

    fun toJson(context: Context): String {
        val dir = File(context.applicationInfo.dataDir, "shared_prefs")
        val files = dir.listFiles { file -> file.extension == "xml" }
            ?.sortedBy { it.name }
            ?: emptyList()

        val array = JSONArray()
        files.forEach { file ->
            val name = file.nameWithoutExtension
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

            val entries = JSONArray()
            prefs.all.entries.sortedBy { it.key }.forEach { entry ->
                val value = entry.value
                val obj = JSONObject()
                obj.put("key", entry.key)
                obj.put("value", value?.toString() ?: "null")
                obj.put("type", value?.javaClass?.simpleName ?: "null")
                entries.put(obj)
            }

            val fileObj = JSONObject()
            fileObj.put("file", name)
            fileObj.put("entries", entries)
            array.put(fileObj)
        }
        return array.toString()
    }
}

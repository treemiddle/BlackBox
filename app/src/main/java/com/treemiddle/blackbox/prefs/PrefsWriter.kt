package com.treemiddle.blackbox.prefs

import android.content.Context

object PrefsWriter {

    fun edit(context: Context, file: String, key: String, type: String, value: String) {
        val editor = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
        when {
            type == "String" -> editor.putString(key, value)
            type == "Integer" -> editor.putInt(key, value.toInt())
            type == "Long" -> editor.putLong(key, value.toLong())
            type == "Float" -> editor.putFloat(key, value.toFloat())
            type == "Boolean" -> editor.putBoolean(key, value.toBoolean())
            type.endsWith("Set") -> editor.putStringSet(key, parseSet(value))
            else -> throw IllegalArgumentException("unsupported type: $type")
        }
        editor.apply()
    }

    fun delete(context: Context, file: String, key: String) {
        context.getSharedPreferences(file, Context.MODE_PRIVATE)
            .edit()
            .remove(key)
            .apply()
    }

    private fun parseSet(value: String): Set<String> =
        value.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}

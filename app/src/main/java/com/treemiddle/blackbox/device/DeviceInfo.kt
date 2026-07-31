package com.treemiddle.blackbox.device

import android.content.Context
import android.os.Build
import org.json.JSONObject

object DeviceInfo {

    fun toJson(context: Context): String =
        JSONObject()
            .put("model", Build.MODEL)
            .put("manufacturer", Build.MANUFACTURER)
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .put("package", context.packageName)
            .toString()
}

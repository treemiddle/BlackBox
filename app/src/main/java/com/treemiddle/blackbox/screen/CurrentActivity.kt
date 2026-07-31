package com.treemiddle.blackbox.screen

import android.app.Activity
import java.lang.ref.WeakReference

object CurrentActivity {

    private var ref: WeakReference<Activity>? = null

    fun set(activity: Activity) {
        ref = WeakReference(activity)
    }

    fun clear(activity: Activity) {
        if (ref?.get() === activity) {
            ref = null
        }
    }

    fun get(): Activity? = ref?.get()
}

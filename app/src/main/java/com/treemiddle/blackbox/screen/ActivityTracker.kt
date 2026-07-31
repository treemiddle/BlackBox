package com.treemiddle.blackbox.screen

import android.app.Activity
import android.app.Application
import android.os.Bundle

object ActivityTracker : Application.ActivityLifecycleCallbacks {

    override fun onActivityResumed(activity: Activity) {
        CurrentActivity.set(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        CurrentActivity.clear(activity)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}

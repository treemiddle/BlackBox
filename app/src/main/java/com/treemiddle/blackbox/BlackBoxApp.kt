package com.treemiddle.blackbox

import android.app.Application
import com.treemiddle.blackbox.screen.ActivityTracker
import com.treemiddle.blackbox.server.BlackBoxServer

class BlackBoxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(ActivityTracker)
        BlackBoxServer.start(this)
    }
}

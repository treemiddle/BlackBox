package com.treemiddle.blackbox

import android.app.Application
import com.treemiddle.blackbox.server.BlackBoxServer

class BlackBoxApp : Application() {

    override fun onCreate() {
        super.onCreate()
        BlackBoxServer.start(this)
    }
}

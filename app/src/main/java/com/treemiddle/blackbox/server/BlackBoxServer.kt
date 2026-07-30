package com.treemiddle.blackbox.server

import android.content.Context
import com.treemiddle.blackbox.capture.NetworkStore
import io.ktor.http.ContentType
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

object BlackBoxServer {

    private const val HOST = "127.0.0.1"
    private const val PORT = 8080

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true

        val indexHtml = context.assets.open("devtools/index.html")
            .bufferedReader()
            .use { it.readText() }

        Thread(
            {
                embeddedServer(CIO, host = HOST, port = PORT) {
                    routing {
                        get("/") {
                            call.respondText(indexHtml, ContentType.Text.Html)
                        }
                        get("/api/network") {
                            call.respondText(NetworkStore.toJson(), ContentType.Application.Json)
                        }
                    }
                }.start(wait = true)
            },
            "BlackBoxServer",
        ).start()
    }
}

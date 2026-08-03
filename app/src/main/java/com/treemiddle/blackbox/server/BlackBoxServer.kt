package com.treemiddle.blackbox.server

import android.content.Context
import android.util.Log
import com.treemiddle.blackbox.capture.NetworkStore
import com.treemiddle.blackbox.db.DbReader
import com.treemiddle.blackbox.device.DeviceInfo
import com.treemiddle.blackbox.prefs.PrefsReader
import com.treemiddle.blackbox.prefs.PrefsWriter
import com.treemiddle.blackbox.record.Recorder
import com.treemiddle.blackbox.screen.ScreenCapture
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import org.json.JSONObject

object BlackBoxServer {

    private const val HOST = "127.0.0.1"
    private const val PORT_START = 8080
    private const val PORT_END = 8089
    private const val HUB_PORT = 8080
    private const val TAG = "BlackBoxServer"

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return

        val port = firstAvailablePort()
        if (port == null) {
            Log.e(TAG, "BlackBox server not started: no free port in $PORT_START..$PORT_END")
            return
        }
        started = true

        val indexHtml = context.assets.open("devtools/index.html")
            .bufferedReader()
            .use { it.readText() }
        val pickerHtml = context.assets.open("devtools/hub.html")
            .bufferedReader()
            .use { it.readText() }

        Thread(
            {
                val server = embeddedServer(CIO, host = HOST, port = port) {
                    routing {
                        if (port == HUB_PORT) {
                            get("/") {
                                call.respondText(pickerHtml, ContentType.Text.Html)
                            }
                            installHub(port, indexHtml)
                        } else {
                            get("/") {
                                call.respondText(indexHtml, ContentType.Text.Html)
                            }
                        }
                        get("/api/device") {
                            call.respondText(DeviceInfo.toJson(context, port), ContentType.Application.Json)
                        }
                        get("/api/screenshot") {
                            val png = ScreenCapture.capturePng()
                            if (png == null) {
                                call.respondText(
                                    JSONObject().put("error", "no visible activity window").toString(),
                                    ContentType.Application.Json,
                                    HttpStatusCode.ServiceUnavailable,
                                )
                            } else {
                                call.respondBytes(png, ContentType.Image.PNG)
                            }
                        }
                        post("/api/record/start") {
                            Recorder.requestStart(context)
                            call.respondText(Recorder.statusJson(), ContentType.Application.Json)
                        }
                        post("/api/record/stop") {
                            Recorder.stop(context)
                            call.respondText(Recorder.statusJson(), ContentType.Application.Json)
                        }
                        get("/api/record/status") {
                            call.respondText(Recorder.statusJson(), ContentType.Application.Json)
                        }
                        get("/api/record/latest") {
                            val recording = Recorder.file
                            if (recording == null || !recording.exists()) {
                                call.respondText(
                                    JSONObject().put("error", "no recording").toString(),
                                    ContentType.Application.Json,
                                    HttpStatusCode.NotFound,
                                )
                            } else {
                                call.respondBytes(recording.readBytes(), ContentType.Video.MP4)
                            }
                        }
                        get("/api/network") {
                            call.respondText(NetworkStore.toJson(), ContentType.Application.Json)
                        }
                        post("/api/network/clear") {
                            NetworkStore.clear()
                            call.respondText(JSONObject().put("ok", true).toString(), ContentType.Application.Json)
                        }
                        get("/api/prefs") {
                            call.respondText(PrefsReader.toJson(context), ContentType.Application.Json)
                        }
                        post("/api/prefs/edit") {
                            try {
                                val json = JSONObject(call.receiveText())
                                PrefsWriter.edit(
                                    context = context,
                                    file = json.getString("file"),
                                    key = json.getString("key"),
                                    type = json.getString("type"),
                                    value = json.getString("value"),
                                )
                                call.respondText(JSONObject().put("ok", true).toString(), ContentType.Application.Json)
                            } catch (e: Exception) {
                                call.respondText(
                                    JSONObject().put("ok", false).put("error", e.message ?: "error").toString(),
                                    ContentType.Application.Json,
                                    HttpStatusCode.BadRequest,
                                )
                            }
                        }
                        post("/api/prefs/delete") {
                            try {
                                val json = JSONObject(call.receiveText())
                                PrefsWriter.delete(
                                    context = context,
                                    file = json.getString("file"),
                                    key = json.getString("key"),
                                )
                                call.respondText(JSONObject().put("ok", true).toString(), ContentType.Application.Json)
                            } catch (e: Exception) {
                                call.respondText(
                                    JSONObject().put("ok", false).put("error", e.message ?: "error").toString(),
                                    ContentType.Application.Json,
                                    HttpStatusCode.BadRequest,
                                )
                            }
                        }
                        get("/api/db") {
                            call.respondText(DbReader.listJson(context), ContentType.Application.Json)
                        }
                        get("/api/db/table") {
                            val name = call.request.queryParameters["db"]
                            val table = call.request.queryParameters["table"]
                            if (name == null || table == null) {
                                call.respondText(
                                    JSONObject().put("error", "db and table required").toString(),
                                    ContentType.Application.Json,
                                    HttpStatusCode.BadRequest,
                                )
                            } else {
                                try {
                                    call.respondText(DbReader.tableJson(context, name, table), ContentType.Application.Json)
                                } catch (e: Exception) {
                                    call.respondText(
                                        JSONObject().put("error", e.message ?: "error").toString(),
                                        ContentType.Application.Json,
                                        HttpStatusCode.BadRequest,
                                    )
                                }
                            }
                        }
                    }
                }
                try {
                    Log.i(TAG, "BlackBox server started on $HOST:$port for ${context.packageName}")
                    server.start(wait = true)
                } catch (e: Exception) {
                    started = false
                    Log.e(TAG, "BlackBox server failed to bind $HOST:$port", e)
                }
            },
            "BlackBoxServer",
        ).start()
    }

    private fun firstAvailablePort(): Int? {
        for (port in PORT_START..PORT_END) {
            if (isPortAvailable(port)) {
                return port
            }
        }
        return null
    }

    private fun isPortAvailable(port: Int): Boolean =
        try {
            ServerSocket().use { socket ->
                socket.bind(InetSocketAddress(InetAddress.getByName(HOST), port))
                true
            }
        } catch (e: IOException) {
            false
        }
}

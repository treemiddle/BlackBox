package com.treemiddle.blackbox.server

import android.content.Context
import com.treemiddle.blackbox.capture.NetworkStore
import com.treemiddle.blackbox.db.DbReader
import com.treemiddle.blackbox.prefs.PrefsReader
import com.treemiddle.blackbox.prefs.PrefsWriter
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import org.json.JSONObject

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
                }.start(wait = true)
            },
            "BlackBoxServer",
        ).start()
    }
}

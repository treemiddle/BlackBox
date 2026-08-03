package com.treemiddle.blackbox.server

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveStream
import io.ktor.server.request.uri
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.json.JSONArray
import org.json.JSONObject

private val APP_ROUTE = Regex("^/app/(\\d+)(/.*)?$")
private const val PORT_START = 8080
private const val PORT_END = 8089

internal fun Route.installHub(hubPort: Int, appIndexHtml: String) {
    get("/hub/apps") {
        call.respondText(discoverApps(), ContentType.Application.Json)
    }
    get("/app/{port}/{path...}") {
        val target = parseTarget(call.request.uri)
        if (target == null) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        if (target.port == hubPort && target.path == "/") {
            call.respondText(appIndexHtml, ContentType.Text.Html)
            return@get
        }
        relay(LoopbackHttp.request(target.port, "GET", target.path, null))
    }
    post("/app/{port}/{path...}") {
        val target = parseTarget(call.request.uri)
        if (target == null) {
            call.respond(HttpStatusCode.NotFound)
            return@post
        }
        val body = call.receiveStream().readBytes()
        relay(LoopbackHttp.request(target.port, "POST", target.path, body))
    }
}

private class Target(val port: Int, val path: String)

private fun parseTarget(uri: String): Target? {
    val match = APP_ROUTE.find(uri) ?: return null
    val port = match.groupValues[1].toIntOrNull() ?: return null
    val path = match.groupValues[2].ifEmpty { "/" }
    return Target(port, path)
}

private suspend fun io.ktor.server.routing.RoutingContext.relay(response: LoopbackHttp.Response?) {
    if (response == null) {
        call.respond(HttpStatusCode.BadGateway)
    } else {
        call.respondBytes(
            response.body,
            ContentType.parse(response.contentType),
            HttpStatusCode.fromValue(response.status),
        )
    }
}

private fun discoverApps(): String {
    val array = JSONArray()
    for (port in PORT_START..PORT_END) {
        val response = LoopbackHttp.request(port, "GET", "/api/device", null) ?: continue
        if (response.status != 200) {
            continue
        }
        try {
            val info = JSONObject(String(response.body))
            array.put(
                JSONObject()
                    .put("package", info.optString("package"))
                    .put("port", port),
            )
        } catch (e: Exception) {
            continue
        }
    }
    return array.toString()
}

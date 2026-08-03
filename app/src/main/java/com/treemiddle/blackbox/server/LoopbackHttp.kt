package com.treemiddle.blackbox.server

import java.net.InetSocketAddress
import java.net.Socket

internal object LoopbackHttp {

    class Response(val status: Int, val contentType: String, val body: ByteArray)

    fun request(port: Int, method: String, pathWithQuery: String, body: ByteArray?): Response? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), CONNECT_TIMEOUT_MS)
                socket.soTimeout = READ_TIMEOUT_MS
                val header = buildString {
                    append("$method $pathWithQuery HTTP/1.0\r\n")
                    append("Host: 127.0.0.1\r\n")
                    if (body != null) {
                        append("Content-Type: application/json\r\n")
                        append("Content-Length: ${body.size}\r\n")
                    }
                    append("\r\n")
                }
                socket.getOutputStream().apply {
                    write(header.toByteArray(Charsets.ISO_8859_1))
                    if (body != null) {
                        write(body)
                    }
                    flush()
                }
                parse(socket.getInputStream().readBytes())
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun parse(raw: ByteArray): Response {
        val separator = indexOfHeaderEnd(raw)
        val headerText = String(raw, 0, if (separator >= 0) separator else raw.size, Charsets.ISO_8859_1)
        val body = if (separator >= 0) raw.copyOfRange(separator + 4, raw.size) else ByteArray(0)
        val lines = headerText.split("\r\n")
        val status = lines.firstOrNull()?.split(" ")?.getOrNull(1)?.toIntOrNull() ?: 200
        val contentType = lines.drop(1)
            .firstOrNull { it.startsWith("Content-Type:", ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim()
            ?: "application/octet-stream"
        return Response(status, contentType, body)
    }

    private fun indexOfHeaderEnd(data: ByteArray): Int {
        for (i in 0..data.size - 4) {
            if (data[i] == CR && data[i + 1] == LF && data[i + 2] == CR && data[i + 3] == LF) {
                return i
            }
        }
        return -1
    }

    private const val CR = 13.toByte()
    private const val LF = 10.toByte()
    private const val CONNECT_TIMEOUT_MS = 2000
    private const val READ_TIMEOUT_MS = 8000
}

package com.treemiddle.blackbox.capture

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

class BlackBoxInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBody = readRequestBody(request)
        val startedAt = System.nanoTime()
        val response = chain.proceed(request)
        val durationMs = (System.nanoTime() - startedAt) / 1_000_000

        val peeked = response.peekBody(MAX_BODY_BYTES)

        NetworkStore.add(
            NetworkEntry(
                id = NetworkStore.nextId(),
                method = request.method,
                url = request.url.toString(),
                code = response.code,
                durationMs = durationMs,
                requestBody = requestBody,
                responseBody = peeked.string(),
            ),
        )
        return response
    }

    private fun readRequestBody(request: Request): String? {
        val body = request.body ?: return null
        if (body.isOneShot()) return null
        return try {
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8(minOf(buffer.size, MAX_BODY_BYTES))
        } catch (e: Exception) {
            null
        }
    }

    private companion object {
        const val MAX_BODY_BYTES = 64L * 1024
    }
}

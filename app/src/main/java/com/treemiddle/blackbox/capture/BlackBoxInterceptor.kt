package com.treemiddle.blackbox.capture

import okhttp3.Interceptor
import okhttp3.Response

class BlackBoxInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
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
                responseBody = peeked.string(),
            ),
        )
        return response
    }

    private companion object {
        const val MAX_BODY_BYTES = 64L * 1024
    }
}

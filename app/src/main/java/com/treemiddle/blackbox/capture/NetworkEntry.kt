package com.treemiddle.blackbox.capture

data class NetworkEntry(
    val id: Long,
    val method: String,
    val url: String,
    val code: Int,
    val durationMs: Long,
    val requestHeaders: Map<String, String>,
    val requestBody: String?,
    val responseHeaders: Map<String, String>,
    val responseBody: String,
)

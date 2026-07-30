package com.treemiddle.blackbox.capture

data class NetworkEntry(
    val id: Long,
    val method: String,
    val url: String,
    val code: Int,
    val durationMs: Long,
    val responseBody: String,
)

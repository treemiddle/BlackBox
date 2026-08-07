package com.treemiddle.blackbox.capture

data class LogEntry(
    val id: Long,
    val time: String,
    val level: String,
    val tag: String,
    val pid: Int,
    val tid: Int,
    val message: String,
)

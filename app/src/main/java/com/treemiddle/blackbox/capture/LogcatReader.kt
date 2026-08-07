package com.treemiddle.blackbox.capture

import android.os.Process
import android.util.Log

object LogcatReader {
    private const val TAG = "LogcatReader"
    private const val RESTART_DELAY_MS = 1000L
    private val LINE = Regex(
        "^(\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d{3})\\s+(\\d+)\\s+(\\d+)\\s+([VDIWEFA])\\s+(.*?):\\s?(.*)$",
    )

    @Volatile
    private var started = false

    fun start() {
        if (started) {
            return
        }
        started = true
        Thread({ loop() }, "LogcatReader").apply { isDaemon = true }.start()
    }

    private fun loop() {
        val myPid = Process.myPid()
        while (true) {
            try {
                read(myPid)
            } catch (e: Exception) {
                Log.e(TAG, "logcat reader stopped, restarting", e)
            }
            Thread.sleep(RESTART_DELAY_MS)
        }
    }

    private fun read(myPid: Int) {
        val process = ProcessBuilder("logcat", "-v", "threadtime")
            .redirectErrorStream(true)
            .start()
        try {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val match = LINE.matchEntire(line) ?: return@forEach
                    val (time, pid, tid, level, tag, message) = match.destructured
                    if (pid.toInt() != myPid) {
                        return@forEach
                    }
                    LogStore.add(
                        time = time,
                        level = level,
                        tag = tag,
                        pid = pid.toInt(),
                        tid = tid.toInt(),
                        message = message,
                    )
                }
            }
        } finally {
            process.destroy()
        }
    }
}

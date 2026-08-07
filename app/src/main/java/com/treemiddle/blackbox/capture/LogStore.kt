package com.treemiddle.blackbox.capture

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

object LogStore {
    private const val MAX_ENTRIES = 3000

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>()
    private val idSeq = AtomicLong(0)

    fun add(
        time: String,
        level: String,
        tag: String,
        pid: Int,
        tid: Int,
        message: String,
    ) {
        synchronized(lock) {
            buffer.addLast(
                LogEntry(
                    id = idSeq.incrementAndGet(),
                    time = time,
                    level = level,
                    tag = tag,
                    pid = pid,
                    tid = tid,
                    message = message,
                ),
            )
            if (buffer.size > MAX_ENTRIES) {
                buffer.removeFirst()
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
        }
    }

    fun toJson(sinceId: Long): String {
        val snapshot = synchronized(lock) {
            buffer.filter { it.id > sinceId }
        }
        val array = JSONArray()
        snapshot.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("time", entry.time)
            obj.put("level", entry.level)
            obj.put("tag", entry.tag)
            obj.put("pid", entry.pid)
            obj.put("tid", entry.tid)
            obj.put("message", entry.message)
            array.put(obj)
        }

        return array.toString()
    }
}

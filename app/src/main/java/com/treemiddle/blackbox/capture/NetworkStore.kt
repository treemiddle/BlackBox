package com.treemiddle.blackbox.capture

import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicLong

object NetworkStore {

    private const val MAX_ENTRIES = 200

    private val lock = Any()
    private val buffer = ArrayDeque<NetworkEntry>()
    private val idSeq = AtomicLong(0)

    fun nextId(): Long = idSeq.incrementAndGet()

    fun add(entry: NetworkEntry) {
        synchronized(lock) {
            buffer.addFirst(entry)
            if (buffer.size > MAX_ENTRIES) {
                buffer.removeLast()
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
        }
    }

    fun toJson(): String {
        val snapshot = synchronized(lock) { buffer.toList() }
        val array = JSONArray()
        snapshot.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("method", entry.method)
            obj.put("url", entry.url)
            obj.put("code", entry.code)
            obj.put("durationMs", entry.durationMs)
            obj.put("requestHeaders", JSONObject(entry.requestHeaders))
            obj.put("requestBody", entry.requestBody ?: "")
            obj.put("responseHeaders", JSONObject(entry.responseHeaders))
            obj.put("responseBody", entry.responseBody)
            array.put(obj)
        }
        return array.toString()
    }
}

package com.treemiddle.blackbox

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.treemiddle.blackbox.capture.BlackBoxInterceptor
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

class MainActivity : Activity() {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(BlackBoxInterceptor())
            .build()
    }

    private val sampleUrls = listOf(
        "https://jsonplaceholder.typicode.com/posts/1",
        "https://jsonplaceholder.typicode.com/users/2",
        "https://api.github.com/repos/treemiddle/BlackBox",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val status = TextView(this).apply {
            text = buildString {
                appendLine("BlackBox server running on 127.0.0.1:8080")
                appendLine()
                appendLine("adb forward tcp:8080 tcp:8080")
                appendLine("→ open http://localhost:8080 in Chrome")
                appendLine()
                append("Tap the button to fire sample requests, then watch them in the browser.")
            }
            setPadding(48, 48, 48, 48)
        }
        val button = Button(this).apply {
            text = "Fire sample requests"
            setOnClickListener { fireSampleRequests() }
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(button)
        }
        setContentView(root)

        seedSamplePrefs()
        seedSampleDb()
        fireSampleRequests()
    }

    private fun seedSamplePrefs() {
        val prefs = getSharedPreferences("blackbox_demo", MODE_PRIVATE)
        val launches = prefs.getInt("launch_count", 0) + 1
        prefs.edit()
            .putString("user_name", "treemiddle")
            .putInt("launch_count", launches)
            .putBoolean("is_debug", true)
            .putLong("last_opened_at", System.currentTimeMillis())
            .putStringSet("tags", setOf("poc", "prefs", "blackbox"))
            .apply()
    }

    private fun seedSampleDb() {
        val db = openOrCreateDatabase("blackbox_demo.db", MODE_PRIVATE, null)
        db.execSQL("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, email TEXT, active INTEGER)")
        db.execSQL("DELETE FROM users")
        db.execSQL("INSERT INTO users (name, email, active) VALUES ('treemiddle', 'tree@tada.io', 1), ('alice', 'alice@example.com', 0), ('bob', 'bob@example.com', 1)")
        db.execSQL("CREATE TABLE IF NOT EXISTS trips (id INTEGER PRIMARY KEY AUTOINCREMENT, city TEXT, fare REAL)")
        db.execSQL("DELETE FROM trips")
        db.execSQL("INSERT INTO trips (city, fare) VALUES ('Singapore', 12.5), ('Bangkok', 8.0), ('Phnom Penh', 5.25)")
        db.close()
    }

    private fun fireSampleRequests() {
        sampleUrls.forEach { url -> enqueue(Request.Builder().url(url).build()) }

        val json = """{"title":"blackbox","body":"hello from poc","userId":7}"""
        val postBody = json.toRequestBody("application/json".toMediaType())
        enqueue(
            Request.Builder()
                .url("https://jsonplaceholder.typicode.com/posts")
                .post(postBody)
                .build(),
        )
    }

    private fun enqueue(request: Request) {
        client.newCall(request).enqueue(
            object : Callback {
                override fun onFailure(call: Call, e: IOException) = Unit

                override fun onResponse(call: Call, response: Response) {
                    response.use { it.body.string() }
                }
            },
        )
    }
}

package com.treemiddle.blackbox

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.treemiddle.blackbox.capture.BlackBoxInterceptor
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
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

        fireSampleRequests()
    }

    private fun fireSampleRequests() {
        sampleUrls.forEach { url ->
            client.newCall(Request.Builder().url(url).build()).enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) = Unit

                    override fun onResponse(call: Call, response: Response) {
                        response.use { it.body.string() }
                    }
                },
            )
        }
    }
}

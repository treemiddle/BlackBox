package com.treemiddle.blackbox.record

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.io.File

object Recorder {

    enum class State { IDLE, REQUESTING, RECORDING, ERROR }

    @Volatile
    private var state: State = State.IDLE

    @Volatile
    var file: File? = null
        private set

    fun requestStart(context: Context) {
        if (state == State.RECORDING || state == State.REQUESTING) return
        state = State.REQUESTING
        val intent = Intent(context, RecordPermissionActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun stop(context: Context) {
        if (state != State.RECORDING) return
        val intent = Intent(context, ScreenRecordService::class.java)
            .setAction(ScreenRecordService.ACTION_STOP)
        context.startService(intent)
    }

    fun onRecording(recorded: File) {
        file = recorded
        state = State.RECORDING
    }

    fun onStopped() {
        state = State.IDLE
    }

    fun onError() {
        state = State.ERROR
    }

    fun statusJson(): String = JSONObject()
        .put("state", state.name.lowercase())
        .put("hasRecording", file?.exists() == true)
        .toString()
}

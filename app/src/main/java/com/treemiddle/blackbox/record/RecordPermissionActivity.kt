package com.treemiddle.blackbox.record

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class RecordPermissionActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST && resultCode == RESULT_OK && data != null) {
            val service = Intent(this, ScreenRecordService::class.java)
                .putExtra(ScreenRecordService.EXTRA_CODE, resultCode)
                .putExtra(ScreenRecordService.EXTRA_DATA, data)
            startForegroundService(service)
        } else {
            Recorder.onError()
        }
        finish()
    }

    private companion object {
        const val REQUEST = 1001
    }
}

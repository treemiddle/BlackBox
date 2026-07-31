package com.treemiddle.blackbox.record

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File

class ScreenRecordService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val resultData = readData(intent)
        if (resultData == null) {
            Recorder.onError()
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundInternal()
        val manager = getSystemService(MediaProjectionManager::class.java)
        val mediaProjection = manager.getMediaProjection(resultCode, resultData)
        if (mediaProjection == null) {
            Recorder.onError()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        projection = mediaProjection
        mediaProjection.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    stopRecording()
                    stopSelf()
                }
            },
            handler,
        )
        startRecording()
        return START_STICKY
    }

    private fun startRecording() {
        val metrics = screenMetrics()
        val maxDimension = maxOf(metrics.widthPixels, metrics.heightPixels)
        val scale = if (maxDimension > 1600) 1600.0 / maxDimension else 1.0
        val width = (metrics.widthPixels * scale).toInt() / 2 * 2
        val height = (metrics.heightPixels * scale).toInt() / 2 * 2

        val output = File(cacheDir, "blackbox-record.mp4")
        val mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(6_000_000)
            setOutputFile(output.absolutePath)
            prepare()
        }
        virtualDisplay = projection?.createVirtualDisplay(
            "blackbox-rec",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder.surface,
            null,
            null,
        )
        mediaRecorder.start()
        recorder = mediaRecorder
        Recorder.onRecording(output)
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Recorder.onError()
        }
        recorder?.reset()
        recorder?.release()
        recorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        projection?.stop()
        projection = null
        Recorder.onStopped()
        stopForegroundCompat()
    }

    private fun startForegroundInternal() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "BlackBox Recording", NotificationManager.IMPORTANCE_LOW),
        )
        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("BlackBox")
            .setContentText("Recording screen…")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    @Suppress("DEPRECATION")
    private fun screenMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager.defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun readData(intent: Intent?): Intent? {
        intent ?: return null
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }
    }

    companion object {
        const val ACTION_STOP = "com.treemiddle.blackbox.STOP_RECORD"
        const val EXTRA_CODE = "code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL = "blackbox_record"
        private const val NOTIF_ID = 42
    }
}

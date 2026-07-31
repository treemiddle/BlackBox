package com.treemiddle.blackbox.screen

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object ScreenCapture {

    fun capturePng(): ByteArray? {
        val activity = CurrentActivity.get() ?: return null
        val window = activity.window ?: return null
        val decor = window.decorView
        val width = decor.width
        val height = decor.height
        if (width <= 0 || height <= 0) {
            return null
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val latch = CountDownLatch(1)
        var success = false
        PixelCopy.request(
            window,
            bitmap,
            { result ->
                success = result == PixelCopy.SUCCESS
                latch.countDown()
            },
            Handler(Looper.getMainLooper()),
        )
        if (!latch.await(3, TimeUnit.SECONDS) || !success) {
            return null
        }

        return ByteArrayOutputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            stream.toByteArray()
        }
    }
}

package com.onvifguard.app.onvif

import android.graphics.Bitmap
import android.graphics.Color
import com.onvifguard.app.data.MotionEvent
import com.onvifguard.app.data.MotionType
import kotlin.math.abs

object VideoMotionDetector {

    // Analyzes a low-res bitmap (e.g. 80x60) against previous frame
    fun compareFrames(current: Bitmap, previous: Bitmap?): MotionEvent? {
        if (previous == null || current.width != previous.width || current.height != previous.height) {
            return null
        }

        val width = current.width
        val height = current.height
        val step = 3 // sample every 3rd pixel for extreme speed (<0.1ms CPU)

        var changedPoints = 0
        var totalPoints = 0
        var centerPointsChanged = 0

        val centerXMin = width / 4
        val centerXMax = (width * 3) / 4
        val centerYMin = height / 5
        val centerYMax = (height * 4) / 5

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                totalPoints++
                val p1 = current.getPixel(x, y)
                val p2 = previous.getPixel(x, y)

                // Luminance comparison
                val l1 = (Color.red(p1) * 299 + Color.green(p1) * 587 + Color.blue(p1) * 114) / 1000
                val l2 = (Color.red(p2) * 299 + Color.green(p2) * 587 + Color.blue(p2) * 114) / 1000

                if (abs(l1 - l2) > 28) {
                    changedPoints++
                    if (x in centerXMin..centerXMax && y in centerYMin..centerYMax) {
                        centerPointsChanged++
                    }
                }
            }
        }

        val diffRatio = changedPoints.toFloat() / totalPoints.toFloat()

        // If > 3.5% of sampled points changed, motion is detected!
        if (diffRatio > 0.035f) {
            val centerRatio = if (changedPoints > 0) centerPointsChanged.toFloat() / changedPoints.toFloat() else 0f
            val isPerson = centerRatio > 0.40f && diffRatio > 0.055f

            return MotionEvent(
                type = if (isPerson) MotionType.PERSON else MotionType.MOTION,
                topic = if (isPerson) "VideoAnalytics/PersonMotion" else "VideoAnalytics/StreamMotion",
                details = if (isPerson) "Person movement detected entering zone" else "Motion activity detected in live stream"
            )
        }

        return null
    }
}

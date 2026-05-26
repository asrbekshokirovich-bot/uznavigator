package com.uznavigator.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.uznavigator.app.R
import kotlin.math.min

/**
 * UZ road-sign style speed limit + current speed display.
 *
 * Layout (vertical):
 *  ┌──────────────────────┐
 *  │   ╔════════════╗    │
 *  │   ║   ┌─────┐  ║    │   ← White circle, thick red border (like real UZ sign)
 *  │   ║   │ 60  │  ║    │   ← Speed limit number — HUGE (90sp+)
 *  │   ║   │km/h │  ║    │   ← unit label small
 *  │   ║   └─────┘  ║    │
 *  │   ╚════════════╝    │
 *  │                      │
 *  │      58 km/h         │   ← Current speed — large (38sp), color-coded
 *  └──────────────────────┘
 *
 *  When no speed limit data: shows "NO LIMIT" in big white text on dark badge.
 *  When over speed: red border pulses + current speed turns red.
 */
class BigSpeedLimitView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class SpeedState { NO_DATA, UNDER, WARNING, OVER }

    private var speedLimitKph: Int? = null
    private var currentSpeedKph: Int = 0
    private var pulseScale = 1f
    private var pulseAnimator: ValueAnimator? = null

    private val dm get() = resources.displayMetrics
    private fun dp(v: Float) = v * dm.density
    private fun sp(v: Float) = v * dm.density * resources.configuration.fontScale

    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val limitNumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val noLimitTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = Color.WHITE
    }
    private val currentSpeedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val currentSpeedUnitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(70, 0, 0, 0)
    }

    private val state: SpeedState
        get() {
            val limit = speedLimitKph ?: return SpeedState.NO_DATA
            return when {
                currentSpeedKph > limit -> SpeedState.OVER
                currentSpeedKph >= limit - 10 -> SpeedState.WARNING
                else -> SpeedState.UNDER
            }
        }

    fun updateSpeedLimit(limitKph: Int?) {
        if (speedLimitKph == limitKph) return
        speedLimitKph = limitKph
        refreshPulse()
        invalidate()
    }

    fun updateCurrentSpeed(speedKph: Int) {
        if (currentSpeedKph == speedKph) return
        currentSpeedKph = speedKph
        refreshPulse()
        invalidate()
    }

    private fun refreshPulse() {
        if (state == SpeedState.OVER) {
            if (pulseAnimator?.isRunning == true) return
            pulseAnimator = ValueAnimator.ofFloat(1f, 1.06f, 1f).apply {
                duration = 700
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    pulseScale = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            pulseAnimator?.cancel()
            pulseAnimator = null
            if (pulseScale != 1f) { pulseScale = 1f; invalidate() }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        // Top section reserved for speed limit circle (75% of height)
        // Bottom section for current speed text (25%)
        val topAreaH = height * 0.72f
        val bottomAreaCy = height * 0.86f

        val radius = min(width * 0.46f, topAreaH * 0.46f) * pulseScale
        val cy = topAreaH / 2f + dp(8f)

        // Drop shadow (offset down)
        canvas.drawCircle(cx, cy + dp(4f), radius * 1.02f, shadowPaint)

        when (state) {
            SpeedState.NO_DATA -> drawNoLimitBadge(canvas, cx, cy, radius)
            else -> drawSpeedLimitSign(canvas, cx, cy, radius)
        }

        // Current speed (below circle)
        drawCurrentSpeed(canvas, cx, bottomAreaCy)
    }

    private fun drawSpeedLimitSign(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // White fill (sign body)
        circleFillPaint.color = Color.WHITE
        canvas.drawCircle(cx, cy, radius, circleFillPaint)

        // Red border — thickness ~14% of radius for proper UZ road-sign feel
        val borderWidth = radius * 0.14f
        borderPaint.strokeWidth = borderWidth
        borderPaint.color = ContextCompat.getColor(context,
            if (state == SpeedState.OVER) R.color.road_sign_red_dark
            else R.color.road_sign_red
        )
        canvas.drawCircle(cx, cy, radius - borderWidth / 2, borderPaint)

        // Speed limit number — HUGE
        val limitText = speedLimitKph?.toString() ?: "—"
        // Auto-size: ~80% of radius
        limitNumberPaint.textSize = radius * 0.95f
        limitNumberPaint.color = ContextCompat.getColor(context, R.color.road_sign_text)

        val fm = limitNumberPaint.fontMetrics
        // Center vertically — adjust slightly upward to make room for km/h underneath
        val numberCy = cy - (fm.ascent + fm.descent) / 2f - radius * 0.10f
        canvas.drawText(limitText, cx, numberCy, limitNumberPaint)

        // "km/h" label below number
        unitPaint.textSize = radius * 0.22f
        unitPaint.color = ContextCompat.getColor(context, R.color.text_secondary)
        canvas.drawText("km/h", cx, cy + radius * 0.55f, unitPaint)
    }

    private fun drawNoLimitBadge(canvas: Canvas, cx: Float, cy: Float, radius: Float) {
        // Dark rounded badge with "NO LIMIT" text
        val w = radius * 2.1f
        val h = radius * 1.55f
        val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
        val cornerR = h * 0.20f

        circleFillPaint.color = ContextCompat.getColor(context, R.color.road_sign_no_limit_bg)
        canvas.drawRoundRect(rect, cornerR, cornerR, circleFillPaint)

        // White text — auto-fit
        noLimitTextPaint.textSize = h * 0.32f
        val fm = noLimitTextPaint.fontMetrics
        val textCy = cy - (fm.ascent + fm.descent) / 2f - h * 0.10f
        canvas.drawText("NO", cx, textCy - h * 0.18f, noLimitTextPaint)
        canvas.drawText("LIMIT", cx, textCy + h * 0.20f, noLimitTextPaint)
    }

    private fun drawCurrentSpeed(canvas: Canvas, cx: Float, cy: Float) {
        // Color-coded current speed
        val speedColor = when (state) {
            SpeedState.OVER -> ContextCompat.getColor(context, R.color.road_sign_red_dark)
            SpeedState.WARNING -> ContextCompat.getColor(context, R.color.speed_warning_border)
            else -> Color.WHITE
        }

        // Render with dark stroke for readability on map
        val numberSize = height * 0.13f
        currentSpeedPaint.textSize = numberSize
        currentSpeedUnitPaint.textSize = numberSize * 0.45f

        // Black outline for visibility on any map background
        val strokePaint = Paint(currentSpeedPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(4f)
            color = Color.argb(180, 0, 0, 0)
        }

        val speedStr = currentSpeedKph.toString()
        canvas.drawText(speedStr, cx, cy, strokePaint)

        currentSpeedPaint.color = speedColor
        canvas.drawText(speedStr, cx, cy, currentSpeedPaint)

        // " km/h" suffix smaller, slightly below
        currentSpeedUnitPaint.color = speedColor
        val strokeUnit = Paint(currentSpeedUnitPaint).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(3f)
            color = Color.argb(180, 0, 0, 0)
        }
        val unitY = cy + numberSize * 0.55f
        canvas.drawText("km/h", cx, unitY, strokeUnit)
        canvas.drawText("km/h", cx, unitY, currentSpeedUnitPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }
}
